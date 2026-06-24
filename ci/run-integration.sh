#!/usr/bin/env bash
# Copyright 2026 Query Farm LLC - https://query.farm
#
# Run this repo's sqllogictest suite (test/sql/*.test) against the vgi-ical
# VGI worker, using a prebuilt standalone `haybarn-unittest` and the signed
# community `vgi` extension — no C++ build from source. See ci/README.md.
#
# Parameterized by TRANSPORT (the same suite, a different VGI transport):
#   subprocess  (default) — VGI_ICAL_WORKER is a stdio command; DuckDB spawns
#                           the fat JAR per attach and speaks Arrow-IPC over its
#                           stdio. cwd of the spawned worker is the stage dir.
#   http        — we start the fat JAR once in `--http` mode bound to a fixed
#                 high port, wait for `/health` to return 200, then point
#                 VGI_ICAL_WORKER at http://127.0.0.1:<port>.
#   unix        — we start the fat JAR once in `--unix <sock>` (AF_UNIX
#                 launcher) mode, wait for the socket to appear, then point
#                 VGI_ICAL_WORKER at unix://<sock>.
#
# For http/unix the worker is started by THIS script (not by DuckDB), so it is
# launched with cwd = the stage dir — the .test files reference fixtures by the
# relative path test/sql/data/*.ics, which the worker resolves against its own
# cwd. (The stage dir is where we cp the committed fixtures.)
#
# Required environment:
#   HAYBARN_UNITTEST   path to the haybarn-unittest binary
#   VGI_ICAL_WORKER    for subprocess: the stdio command (e.g.
#                      `java -jar /abs/path/vgi-ical-<ver>-all.jar`).
#                      For http/unix it is computed here, so set
#                      VGI_ICAL_WORKER_JAR (or VGI_ICAL_WORKER as the bare jar
#                      command) instead — see below.
# Optional:
#   TRANSPORT          subprocess | http | unix          (default: subprocess)
#   VGI_ICAL_WORKER_JAR  abs path to the fat JAR (required for http/unix; if
#                        unset, derived from VGI_ICAL_WORKER by stripping a
#                        leading `java -jar `).
#   HTTP_PORT          fixed port for http mode           (default: 18110)
#   STAGE              scratch dir for the preprocessed test tree (default: mktemp)
set -euo pipefail

TRANSPORT="${TRANSPORT:-subprocess}"

: "${HAYBARN_UNITTEST:?path to the haybarn-unittest binary}"

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
STAGE="${STAGE:-$(mktemp -d)}"

echo "Transport: $TRANSPORT"

echo "Staging preprocessed tests into $STAGE ..."
mkdir -p "$STAGE/test/sql"
for f in "$REPO"/test/sql/*.test; do
  awk -f "$HERE/preprocess-require.awk" "$f" > "$STAGE/test/sql/$(basename "$f")"
done

# The .test files reference committed fixtures under test/sql/data via relative
# paths; stage them alongside the preprocessed tests so the runner (which cd's
# into $STAGE) and the out-of-band http/unix worker (started with cwd=$STAGE)
# both resolve them.
if [ -d "$REPO/test/sql/data" ]; then
  cp -R "$REPO/test/sql/data" "$STAGE/test/sql/data"
fi

# ---------------------------------------------------------------------------
# Bring up the worker transport and export VGI_ICAL_WORKER for the .test files.
# ---------------------------------------------------------------------------
WORKER_PID=""
cleanup() {
  if [ -n "$WORKER_PID" ]; then
    kill "$WORKER_PID" 2>/dev/null || true
    wait "$WORKER_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# Resolve the fat JAR command for http/unix. Prefer VGI_ICAL_WORKER_JAR; else
# strip a leading `java -jar ` from VGI_ICAL_WORKER.
resolve_jar() {
  if [ -n "${VGI_ICAL_WORKER_JAR:-}" ]; then
    printf '%s' "$VGI_ICAL_WORKER_JAR"
    return
  fi
  if [ -n "${VGI_ICAL_WORKER:-}" ]; then
    # e.g. "java -jar /abs/x-all.jar" -> "/abs/x-all.jar"
    printf '%s' "${VGI_ICAL_WORKER#java -jar }"
    return
  fi
  echo "::error::http/unix transport needs VGI_ICAL_WORKER_JAR (or VGI_ICAL_WORKER as the jar command)" >&2
  exit 1
}

case "$TRANSPORT" in
  subprocess)
    : "${VGI_ICAL_WORKER:?worker LOCATION (stdio command) for subprocess transport}"
    echo "subprocess worker: $VGI_ICAL_WORKER"
    ;;

  http)
    JAR="$(resolve_jar)"
    PORT="${HTTP_PORT:-18110}"
    echo "Starting fat JAR in --http mode on 127.0.0.1:$PORT (cwd=$STAGE) ..."
    # cwd = $STAGE so relative fixture paths (test/sql/data/*.ics) resolve.
    ( cd "$STAGE" && exec java -jar "$JAR" --http --host 127.0.0.1 --port "$PORT" ) \
      > "$STAGE/worker-http.log" 2>&1 &
    WORKER_PID=$!

    # Readiness: poll /health until it returns 200 (and the worker is alive).
    ready=""
    for _ in $(seq 1 60); do
      if ! kill -0 "$WORKER_PID" 2>/dev/null; then
        echo "::error::http worker exited during startup; log follows:" >&2
        cat "$STAGE/worker-http.log" >&2 || true
        exit 1
      fi
      if curl -fsS "http://127.0.0.1:$PORT/health" -o /dev/null 2>/dev/null; then
        ready=1
        break
      fi
      sleep 0.5
    done
    if [ -z "$ready" ]; then
      echo "::error::http worker /health never returned 200; log follows:" >&2
      cat "$STAGE/worker-http.log" >&2 || true
      exit 1
    fi
    echo "http worker healthy on port $PORT (also advertised PORT:<n> on stdout)"
    export VGI_ICAL_WORKER="http://127.0.0.1:$PORT"
    ;;

  unix)
    JAR="$(resolve_jar)"
    SOCK="${UNIX_SOCK:-$STAGE/vgi-ical.sock}"
    echo "Starting fat JAR in --unix mode on $SOCK (cwd=$STAGE) ..."
    ( cd "$STAGE" && exec java -jar "$JAR" --unix "$SOCK" ) \
      > "$STAGE/worker-unix.log" 2>&1 &
    WORKER_PID=$!

    # Readiness: wait for the AF_UNIX socket file to appear (and worker alive).
    ready=""
    for _ in $(seq 1 60); do
      if ! kill -0 "$WORKER_PID" 2>/dev/null; then
        echo "::error::unix worker exited during startup; log follows:" >&2
        cat "$STAGE/worker-unix.log" >&2 || true
        exit 1
      fi
      if [ -S "$SOCK" ]; then
        ready=1
        break
      fi
      sleep 0.5
    done
    if [ -z "$ready" ]; then
      echo "::error::unix worker socket never appeared; log follows:" >&2
      cat "$STAGE/worker-unix.log" >&2 || true
      exit 1
    fi
    echo "unix worker listening on $SOCK"
    export VGI_ICAL_WORKER="unix://$SOCK"
    ;;

  *)
    echo "::error::unknown TRANSPORT '$TRANSPORT' (want subprocess|http|unix)" >&2
    exit 1
    ;;
esac

cd "$STAGE"

# Warm the extension cache once: vgi from the signed community channel, plus
# httpfs from core (the vgi HTTP transport requires httpfs for DuckDB's HTTP
# client). A miss here is only a warning — but it is what provisions the signed
# extensions so each test file's explicit `LOAD vgi;` / `require httpfs` succeed
# on a clean runner. httpfs is harmless for the subprocess/unix transports.
echo "Warming the extension cache (vgi from community, httpfs from core) ..."
mkdir -p "$STAGE/test"
cat > "$STAGE/test/_warm.test" <<'EOF'
# name: test/_warm.test
# group: [warm]
statement ok
INSTALL vgi FROM community;

statement ok
INSTALL httpfs FROM core;
EOF
"$HAYBARN_UNITTEST" "test/_warm.test" >/dev/null 2>&1 || echo "::warning::extension warm step did not fully succeed"
rm -f "$STAGE/test/_warm.test"

# Run the whole suite in one invocation, streaming the runner's native
# sqllogictest report. Any failed assertion exits non-zero and fails the job.
echo "Running suite (transport: $TRANSPORT, worker: $VGI_ICAL_WORKER) ..."
"$HAYBARN_UNITTEST" "test/sql/*"
