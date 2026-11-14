# iCalendar feed quality and occupied time

[01 · Meeting durations double-count overlapping time](01_measure_occupied_time_without_double_counting.ipynb)
asks how much time an explicitly selected set of calendar events occupies.
[Open in Colab](https://colab.research.google.com/github/Query-farm/vgi-ical/blob/main/notebooks/01_measure_occupied_time_without_double_counting.ipynb)
and choose **Runtime → Run all**. The notebook first checks two built-in sample
feeds, then parses an authored calendar, declares a timed-event policy and merges
overlapping reservations in visible SQL. Two SVG charts compare individual
reservations with their union and summed duration with occupied time.

The built-in feeds are separate controls: three stored VEVENT components across
two feeds, including one weekly recurring component. Parsed component count does
not count expanded occurrences. Those controls never enter the schedule study.
The study contains nine synthetic events and three to-dos. Four confirmed,
one-off timed events sum to **210 minutes** and occupy **180 minutes**. With a
15-minute buffer on each side, the sum is **330 minutes** and the union is **240**.
The fixed 08:00–17:00 UTC window and original event times remain unchanged.
This subset does not establish a person's complete availability.

The base shared package supports Colab without replacing compatible kernel and
plotting libraries. It retrieves immutable SHA256-verified helpers and fixture
bytes. The local locked publication environment additionally uses `[runner]`:

```sh
uv sync --locked
uv run --locked vgi-notebooks
uv run --locked vgi-notebooks 01_measure_occupied_time_without_double_counting.ipynb --parameters '{"BUFFER_MINUTES":15}' --output-dir artifacts/buffer-15
```

Run these commands from `notebooks/`; the directory also works independently of
the worker checkout. For interactive local Jupyter select `.venv/bin/python`.
The runner provisions a temporary kernel, rejects unknown overrides and writes
experiments to separate destinations. Failed runs preserve the saved default.
If an earlier bootstrap replaced kernel libraries, save your work, disconnect
and delete that runtime, then open the current notebook in a fresh runtime.

The released worker launch remains visible and literal:

```sql
INSTALL vgi FROM community;
LOAD vgi;
ATTACH 'ical' (TYPE vgi,
 LOCATION 'uv run --locked --no-project --python 3.13 ical_release.py v0.4.1');
```

[`ical_release.py`](ical_release.py) pins the public
[v0.4.1 JAR](https://github.com/Query-farm/vgi-ical/releases/tag/v0.4.1), verifies
SHA256 `f575983fd1ac579adc6b150bbaafc86606933db8968be8b868168a80670378cf`
on download and every cached launch, and starts a managed **Java 21.0.8** binary.
Its separate script lock pins `jdk4py==21.0.8.2` and wheel hashes. The launcher
checks the actual runtime binary version and never selects Java from PATH or
JAVA_HOME. No system Java installation, Gradle build or worker checkout is required.
The script environment is separate from the notebook kernel.

Initial setup downloads Python 3.13 if needed, the approximately 30–34 MB Java
runtime wheel and the approximately 36 MB JAR. The POSIX-lock launcher supports
macOS and Linux with compatible native wheels (ARM64/x86-64; Linux glibc 2.17+).
A bounded lock serializes JAR downloads and completed files are published atomically.
The JAR cache is `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`; uv manages the
script runtime cache. Setup uses network access; calendar processing itself is
local. The local lock retains Haybarn **2.0.0rc11**. Adopting another worker release
requires a reviewed asset digest and repeating the catalog/default/experiment checks.

The [shared v0.3.9 standard](https://github.com/Query-farm/vgi-example-notebooks/blob/v0.3.9/docs/standard.md)
provides catalog-derived title/subtitle branding, isolated SQL secrets, SVG
presentation and execution protections. `ical_example.py` validates the visible
parameter and presents returned results. Keep committed `charts/` SVG files with
the saved notebook for GitHub rendering; Colab/Jupyter reruns emit native SVG.
PNG review exports and execution artifacts remain ignored.

Coverage is **6 of 6 public named SQL surfaces**, with path/BLOB overloads counted
once: `example_calendars`, `ical_calendar_name`, `ical_event_count`, `is_valid_ical`,
`ical_events`, and `ical_todos`. `coverage.json` binds complete released/current
runtime catalogs, full view and table-function columns, successful execution
receipts and reviewed narrative/SQL/result mappings. Current source changes
composed example metadata and descriptions but matches the released interfaces.
The notebook executes the pinned release; compatible current mappings do not claim
current-main notebook execution. No public surfaces are excluded.

All three fixture files are authored and retain their original CRLF bytes:

- [synthetic-day.ics](assets/synthetic-day.ics): nine VEVENTs and three VTODOs, with explicit UTC timed values.
- [empty.ics](assets/empty.ics): a parseable calendar with no event components.
- [unparseable.ics](assets/unparseable.ics): deliberately invalid text.

A VARCHAR argument is a worker-local path; `read_blob` and `feed::BLOB` demonstrate
scalar calls over bytes. Table functions require literal inputs and cannot bind
column/LATERAL arguments. The built-in view is an authored source of controls,
not a fetched calendar. A successful parse alone does not guarantee every component
of an arbitrary feed survived relaxed parsing. Empty and invalid feeds both return
zero event counts but different parse flags.

The schedule policy includes only confirmed, non-recurring, timed events with
usable projected start/end bounds. Cancelled, tentative, all-day, recurring and
missing-end rows stay in the classified inventory. The release retains RRULE text
without expanding occurrences. It also does not project DURATION or derive DTEND
from it; the duration-only fixture retains an unknown end. No fabricated occurrence
or implicit zero-duration interval is added. All-day dates remain separate from
timed meetings. This sample does not test floating times, zoned-time/DST rules or
complete recurrence semantics. [RFC 5545](https://datatracker.ietf.org/doc/html/rfc5545)
defines the underlying iCalendar properties.

`BUFFER_MINUTES` accepts integers from **0 through 60**. Change only **0 to 15**,
restart and run all. Reservations extend on both sides, then clip to
**08:00–17:00 UTC on 5 January 2026**. The model uses half-open intervals; an end
that touches another start is not an overlapping pair. The union compares each
start with the maximum preceding end, handling nested reservations. Touching
intervals may form a contiguous occupied block. Sum minus union measures duplicate
counting, not generally the distinct minutes with any conflict. An independent
one-minute grid checks the union for these minute-aligned boundaries. Reset to 0.

VTODO due times are shown separately from reservations and event relationships.
Their stored completion status is not independent evidence of completed work.
The thirteen checks reconcile sample metadata, event populations, scope categories,
recurrence retention, missing bounds, clipping, parse flags and the occupied union.

Validation includes a default run, a detached buffer-15 experiment, reviewed charts,
three runner guards, no-system-Java/fake-Java11 SQL probes and a fresh preloaded
Colab-compatible kernel bootstrap. Hosted Colab was not manually operated.
Source checks preserve worker code and root locks: 16 JUnit tests, 205 assertions
in five SQL cases on each stdio/HTTP/Unix transport, and metadata 100/100 L2 with
zero findings. Worker code is MIT licensed; iCal4j uses BSD-3-Clause.
