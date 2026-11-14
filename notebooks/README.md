# iCalendar notebooks

[01 · Meeting durations double-count overlapping time](01_measure_occupied_time_without_double_counting.ipynb)
parses an entirely synthetic calendar, declares which event rows enter a timed
schedule, and computes their occupied interval union in SQL. A timeline and duration
chart show why summing individual reservations counts overlaps more than once.

Four confirmed, one-off timed events sum to 210 minutes and occupy 180 minutes.
Changing `BUFFER_MINUTES` from 0 to 15 reserves time on both sides of each event:
the sum becomes 330 minutes while the union occupies 240 minutes. The original
event times, nine-event inventory, and 08:00–17:00 UTC window remain unchanged.
This selected subset does not establish anyone's complete availability.

## Run

Install [uv](https://docs.astral.sh/uv/) and Java 21 or newer on `PATH`. On macOS
with Homebrew Java, add `/opt/homebrew/opt/openjdk@21/bin` if needed. The release
helper uses macOS/Linux POSIX file locks. Initial installation needs network access
for the Python environment, VGI extension, and approximately 36 MB JAR; calendar
processing itself is local.

From the repository root:

```sh
uv sync --project notebooks --frozen
uv run --project notebooks --frozen python notebooks/execute.py
```

The complete `notebooks/` directory also runs independently of the worker checkout:

```sh
cd notebooks
uv sync --frozen
uv run --frozen python execute.py
```

In an editor, select `notebooks/.venv/bin/python`, use `notebooks/` as the kernel
working directory, and restart/run all. The connection, analytical parameter and
SQL remain visible; import, plot, assertion and cleanup code is folded. Charts use
Seaborn styling and Matplotlib and are embedded in the saved default notebook.

Run the single-parameter experiment without replacing the saved default:

```sh
uv run --project notebooks --frozen python notebooks/execute.py \
  --parameters '{"BUFFER_MINUTES":15}' \
  --output-dir notebooks/validation/buffer-15
```

The runner rejects unknown parameters, requires a separate directory for overrides,
and saves a notebook only after successful execution in a fresh kernel. Review PNGs
are written under ignored `notebooks/validation/charts/`. The supported buffer range
is an integer from 0 to 60 minutes.

## Portable worker and data

The connection uses this literal location:

```sql
ATTACH 'ical' (TYPE vgi,
 LOCATION 'uv run --frozen --project . python release_jar.py Query-farm/vgi-ical v0.4.1 vgi-ical-v0.4.1.jar f575983fd1ac579adc6b150bbaafc86606933db8968be8b868168a80670378cf');
```

[`release_jar.py`](release_jar.py) downloads the public
[v0.4.1 JAR](https://github.com/Query-farm/vgi-ical/releases/tag/v0.4.1), verifies its
pinned SHA-256 on download and every cached launch, then starts Java. A bounded
lock serializes downloads and completed files are published atomically. The cache
is `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`. No Gradle, worker source build,
credentials, or sibling checkout is required. The separate notebook lock uses
Haybarn 2.0.0rc11 and leaves the repository's Java dependencies unchanged.

All three calendar fixtures are authored for the notebook:

- [synthetic-day.ics](assets/synthetic-day.ics): nine VEVENT components and three
  VTODO components, all timed values explicitly UTC.
- [empty.ics](assets/empty.ics): a parseable calendar with no event components.
- [unparseable.ics](assets/unparseable.ics): deliberately invalid text.

The path overload reads these files; `read_blob` demonstrates scalar calls over
their bytes. A VARCHAR argument is always a path, so inline feed text needs BLOB
encoding. Table functions require literal arguments; scalars accept columns.

## Scope and interpretation

The explicit schedule policy includes only confirmed, non-recurring, timed events
with positive projected start/end intervals. Cancelled, tentative, all-day,
recurring and missing-end rows remain in the classified inventory. The release
returns RRULE text but does not expand recurrence. It also does not project DURATION
or derive DTEND from it: the duration-only fixture therefore retains an unknown end
for this calculation. No implicit zero duration or fabricated occurrence is added.

All timestamps used for analysis are explicitly converted to UTC in SQL. The sample
does not use floating times or test zoned-time/DST behavior. All-day dates remain
separate from timed meetings. VTODO due times are shown in a separate status table;
they are not interpreted as meeting reservations or event links.

The union algorithm compares each start with the maximum preceding end, correctly
handling nested intervals. Touching intervals form a contiguous occupied block but
do not count as overlapping pairs. Sum minus union measures duplicate counting;
it is not generally the number of distinct minutes with any conflict. An independent
one-minute grid validates the union for this fixture's minute-aligned boundaries.

The twelve checks also reconcile source event counts, scope classification,
recurrence retention, missing bounds, window clipping, parse flags, and separate
to-dos. Empty and unparseable inputs both produce zero event counts but different
parse flags. Relaxed parsing can skip components in arbitrary feeds, so a successful
parse is not a completeness guarantee.

Validation includes executed defaults, the buffer-15 experiment from a detached
notebooks-only directory, four reviewed charts, runner guards and notebook lint.
Repository checks passed 16 JUnit tests, 205 SQL assertions across five cases on each
of stdio, HTTP and Unix transports, and executed vgi-lint 2.5.1 at 100/100 L2 with
zero findings. Source checks build the worker for testing; notebook runs use the
released JAR. Worker code is MIT licensed; iCal4j uses BSD-3-Clause.

Unmodified official brand assets link to [Query.Farm](https://query.farm/)
and [VGI](https://query.farm/vgi/).
