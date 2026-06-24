# CI: the vgi-ical worker integration suite

[`.github/workflows/test.yml`](../.github/workflows/test.yml) runs the Java unit
tests, builds the fat JAR, and runs this repo's sqllogictest suite
(`test/sql/*.test`) against the vgi-ical VGI worker through the **real DuckDB
`vgi` extension** on every push / PR.

## How it works (no C++ build)

Rather than building the vgi DuckDB extension from source, CI drives a
**prebuilt** standalone `haybarn-unittest` (the DuckDB/Haybarn sqllogictest
runner, published in Haybarn's releases) and installs the **signed** `vgi`
extension from the Haybarn community channel:

1. **Build the worker** — `./gradlew shadowJar` produces a self-contained fat
   JAR at `build/libs/vgi-ical-<ver>-all.jar`. Its manifest sets `Add-Opens`, so
   a bare `java -jar <jar>` is a self-contained stdio worker the extension can
   spawn as a subprocess (the VGI LOCATION).
2. **Download the runner** — the `haybarn_unittest-linux-amd64.zip` asset from
   the latest Haybarn release (resolved once in the `resolve-haybarn` job so the
   version is never hardcoded).
3. **Preprocess** — the standalone runner links none of the extensions the
   tests gate on, so [`preprocess-require.awk`](preprocess-require.awk) rewrites
   each `require <ext>` into an explicit signed `INSTALL <ext> FROM
   {community,core}; LOAD <ext>;`. `require-env` and everything else pass
   through untouched.
4. **Run** — [`run-integration.sh`](run-integration.sh) stages the preprocessed
   tree plus the committed `.ics` fixtures, brings up the worker on the chosen
   transport, points `VGI_ICAL_WORKER` at it, **warms the extension cache once**
   (`INSTALL vgi FROM community;`) so each test file's explicit `LOAD vgi;`
   succeeds, then runs the suite in a single `haybarn-unittest` invocation. Any
   failed assertion exits non-zero and fails the job.

## Transport matrix

The `vgi` extension chooses the transport from the worker `LOCATION` string, so
the **same suite** exercises a different transport just by changing what
`VGI_ICAL_WORKER` resolves to. `run-integration.sh` is parameterized by a
`TRANSPORT` env var (default `subprocess`), and the `integration` CI job runs it
once per transport (`strategy.matrix.transport: [subprocess, http, unix]`):

| `TRANSPORT`  | `VGI_ICAL_WORKER`        | how the worker is started | readiness gate |
|--------------|-------------------------|---------------------------|----------------|
| `subprocess` | `java -jar <jar>`       | DuckDB spawns it per attach (Arrow-IPC over stdio) | n/a |
| `http`       | `http://127.0.0.1:<p>`  | this script boots `java -jar <jar> --http --host 127.0.0.1 --port <p>` (default 18110) | poll `GET /health` until HTTP 200 |
| `unix`       | `unix://<sock>`         | this script boots `java -jar <jar> --unix <sock>` | wait for the AF_UNIX socket file to appear |

The vgi Java SDK's `Worker.runFromArgs` (farm.query:vgi) supports these flags
directly: `--http`/`--host`/`--port` (it serves `/health` and also prints
`PORT:<n>` to stdout, so `--port 0` would advertise the chosen port), and
`--unix <path>` (the AF_UNIX launcher), plus an optional `--idle-timeout`.

For `http`/`unix` the worker is started **by this script** rather than by
DuckDB, so it is launched with **cwd = the stage dir** — the `.test` files
reference fixtures by the relative path `test/sql/data/*.ics`, which the worker
resolves against its own cwd. (For `subprocess`, DuckDB spawns the worker with
that same cwd.) In all three cases the committed `.ics` fixtures are staged into
the stage dir, so fixture resolution is identical across transports.

The suite itself is transport-agnostic — pure table-function / scalar reads with
SQL-side `ORDER BY` (the runner sorts results), no inline log streaming,
partition-local state, input-buffering, or wire-order-dependent assertions — so
no test needs a per-transport gate; all three legs run the full suite.

### httpfs is required for the HTTP transport

The `vgi` extension's HTTP transport uses DuckDB's HTTP client, which lives in
the **`httpfs`** extension — without it, ATTACH over an `http://` LOCATION raises
`VGI HTTP transport requires the httpfs extension`. On the standalone
`haybarn-unittest` runner, extension auto-load/auto-install is off, so `httpfs`
must be installed and loaded explicitly. Each `.test` therefore carries a
`require httpfs` line (preprocessed into `INSTALL httpfs FROM core; LOAD
httpfs;`) and the warm step installs it once. `httpfs` is a harmless no-op for
the subprocess/unix transports.

This was the one real failure surfaced by adding the http leg: before the
`httpfs` fix, the http job appeared green but every test was **silently skipped**
— some `haybarn-unittest` builds carry a built-in `skip on error_message
matching 'HTTP'` rule, and the `httpfs`-missing BinderException contains the
substring `HTTP`, so the runner skipped rather than failed. Always confirm the
http leg reports `All tests passed`, not `All tests were skipped`.

## Run it locally

```bash
./gradlew shadowJar                         # build the fat JAR
JAR="$PWD/build/libs/$(ls build/libs | grep -- -all.jar | head -1)"

# subprocess (default):
HAYBARN_UNITTEST=/path/to/haybarn-unittest \
VGI_ICAL_WORKER="java -jar $JAR" \
  ci/run-integration.sh

# http / unix: the script boots the JAR itself, so give it the JAR path:
HAYBARN_UNITTEST=/path/to/haybarn-unittest TRANSPORT=http \
VGI_ICAL_WORKER_JAR="$JAR" ci/run-integration.sh
HAYBARN_UNITTEST=/path/to/haybarn-unittest TRANSPORT=unix \
VGI_ICAL_WORKER_JAR="$JAR" ci/run-integration.sh
```

Or use `make test-sql`, which builds the JAR and runs the (subprocess) suite via
a `haybarn-unittest` already on PATH.

> Note: some prebuilt `haybarn-unittest` binaries carry a built-in
> `skip on error_message matching 'HTTP'` rule that silently skips a `.test`
> file whenever *any* error string contains `HTTP` — including unrelated local
> issues like a community-extension download miss for your dev platform. If the
> `http` leg reports "All tests were skipped" locally, that is this masking rule,
> not a worker fault; the Linux CI runner (where the signed extension installs
> cleanly) is the source of truth for the `http` leg.
