<p align="center">
  <img src="https://query.farm/media-kit/products/vgi/vgi-logo.png" alt="Vector Gateway Interface (VGI)" width="320">
</p>

<p align="center"><em>A <a href="https://query.farm">Query.Farm</a> VGI worker for DuckDB.</em></p>

# Parse iCalendar (.ics) Events & To-Dos in DuckDB

> **vgi-ical** · a [Query.Farm](https://query.farm) VGI worker · powered by iCal4j

[![test](https://github.com/Query-farm/vgi-ical/actions/workflows/test.yml/badge.svg)](https://github.com/Query-farm/vgi-ical/actions/workflows/test.yml)

A [VGI](https://query.farm) worker that brings [iCal4j](https://www.ical4j.org/)
into DuckDB/SQL: parse iCalendar (`.ics`) feeds — **events, todos, and calendar
metadata** — into relational rows, as SQL table and scalar functions.

Written in **Java** because iCal4j is the mature, RFC 5545–complete iCalendar
stack on the JVM (VTIMEZONE resolution, recurrence rules, compatibility-relaxed
parsing for real-world Google/Outlook/Apple exports) with no equal elsewhere.

```sql
INSTALL vgi FROM community; LOAD vgi;
ATTACH 'ical' (TYPE vgi, LOCATION 'java -jar vgi-ical-v0.4.1.jar');

-- one calendar file -> one row per event, sorted by start time
SELECT summary, dtstart, location
FROM ical.ical_events('/cal/team.ics')
ORDER BY dtstart;

-- a whole directory of feeds: the scalars take column arguments
SELECT parse_filename(filename) AS file, ical.is_valid_ical(content) AS ok,
       ical.ical_event_count(content) AS events
FROM read_blob('/cal/*.ics')
ORDER BY file;
```

## Quick start

For an executed SQL walkthrough with charts and a portable release connection, see
the [iCalendar notebooks](notebooks/README.md).

Every [GitHub release](https://github.com/Query-farm/vgi-ical/releases) ships a self-contained worker jar. It needs Java 21 or newer. Download it:

```sh
curl -LO https://github.com/Query-farm/vgi-ical/releases/download/v0.4.1/vgi-ical-v0.4.1.jar
```

then attach it from DuckDB (run from the directory holding the jar, or use its absolute path):

```sql
INSTALL vgi FROM community;
LOAD vgi;

ATTACH 'ical' AS ical (TYPE vgi, LOCATION 'java -jar vgi-ical-v0.4.1.jar');

-- List the events in an iCalendar feed.
SELECT summary, location FROM ical.main.ical_events(array_to_string(['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Query Farm//vgi-ical//EN','X-WR-CALNAME:Team Calendar','BEGIN:VEVENT','UID:event-1@query.farm','SUMMARY:Sprint Planning','DESCRIPTION:Plan the next two-week sprint.','DTSTART:20240115T093000Z','DTEND:20240115T103000Z','LOCATION:Conference Room A','STATUS:CONFIRMED','ORGANIZER:mailto:alice@query.farm','ATTENDEE:mailto:bob@query.farm','ATTENDEE:mailto:carol@query.farm','RRULE:FREQ=WEEKLY;BYDAY=MO','SEQUENCE:3','END:VEVENT','BEGIN:VTODO','UID:todo-1@query.farm','SUMMARY:Write release notes','DUE:20240120T170000Z','STATUS:NEEDS-ACTION','PRIORITY:2','PERCENT-COMPLETE:40','END:VTODO','END:VCALENDAR'], chr(10))::BLOB) ORDER BY dtstart;

-- List the to-dos in an iCalendar feed.
SELECT summary, status FROM ical.main.ical_todos(array_to_string(['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//Query Farm//vgi-ical//EN','X-WR-CALNAME:Team Calendar','BEGIN:VEVENT','UID:event-1@query.farm','SUMMARY:Sprint Planning','DESCRIPTION:Plan the next two-week sprint.','DTSTART:20240115T093000Z','DTEND:20240115T103000Z','LOCATION:Conference Room A','STATUS:CONFIRMED','ORGANIZER:mailto:alice@query.farm','ATTENDEE:mailto:bob@query.farm','ATTENDEE:mailto:carol@query.farm','RRULE:FREQ=WEEKLY;BYDAY=MO','SEQUENCE:3','END:VEVENT','BEGIN:VTODO','UID:todo-1@query.farm','SUMMARY:Write release notes','DUE:20240120T170000Z','STATUS:NEEDS-ACTION','PRIORITY:2','PERCENT-COMPLETE:40','END:VTODO','END:VCALENDAR'], chr(10))::BLOB) ORDER BY due;
```

### Other ways to run it

- **Docker** (the image bundles the worker and a Java runtime):

  ```sql
  ATTACH 'ical' AS ical (TYPE vgi, LOCATION 'docker://ghcr.io/query-farm/vgi-ical:0.4.1');
  ```

### Build from source

To build the worker jar yourself, see [Build & test](#build--test).

## How iCalendar maps onto SQL

| Area | SQL surface | VGI primitive |
| --- | --- | --- |
| **Events** | `SELECT * FROM ical.ical_events(path \| bytes)` | table function (1 feed → N event rows) |
| **Todos** | `SELECT * FROM ical.ical_todos(path \| bytes)` | table function (1 feed → N todo rows) |
| **Calendar name** | `ical.ical_calendar_name(path \| bytes)` | scalar (VARCHAR) |
| **Event count** | `ical.ical_event_count(path \| bytes)` | scalar (INT) |
| **Validity** | `ical.is_valid_ical(path \| bytes)` | scalar (BOOLEAN) |

### Path-or-bytes input

Every function has two typed overloads, each taking one argument: a **VARCHAR
path** (the worker opens the `.ics` file) or a **BLOB** of `.ics` bytes that
travelled over Arrow. Unsupported argument types are rejected at SQL bind time
instead of being converted to file paths. A `NULL` argument yields `NULL` / no
rows.

A `VARCHAR` is always read as a path, so `.ics` *text* held in a column must be
cast first (`feed::BLOB`); passed uncast, the worker looks for a file of that name
and returns `false` / `NULL` / `0`.

The **scalars** accept column arguments, so they run once per row. The **table
functions** bind only literal arguments: `ical_events(t.feed)`, a `LATERAL` call
or a subquery argument fails with *Binder Error: Table function cannot contain
subqueries*. Pass a path or a BLOB literal, and use the scalars (or
`read_blob()`, below) to work across many feeds.

## Functions

### `ical_events(input) → table`

One row per `VEVENT`:

| Column | Type | Notes |
| --- | --- | --- |
| `uid` | VARCHAR | `UID` |
| `summary` | VARCHAR | `SUMMARY` (title) |
| `description` | VARCHAR | `DESCRIPTION` |
| `dtstart` | TIMESTAMP | `DTSTART`, UTC-normalised (see below) |
| `dtend` | TIMESTAMP | `DTEND`, UTC-normalised, or NULL |
| `all_day` | BOOLEAN | true when `DTSTART` is `DATE`-valued |
| `location` | VARCHAR | `LOCATION` |
| `status` | VARCHAR | `CONFIRMED` / `TENTATIVE` / `CANCELLED` |
| `organizer` | VARCHAR | `ORGANIZER` (usually a `mailto:` URI) |
| `attendees` | VARCHAR[] | one element per `ATTENDEE` |
| `rrule` | VARCHAR | `RRULE`, or NULL for one-off events |
| `sequence` | INT | `SEQUENCE` revision number |
| `categories` | VARCHAR[] | `CATEGORIES` tags, split on commas |
| `url` | VARCHAR | `URL`, or NULL |
| `created` | TIMESTAMP | `CREATED`, UTC-normalised, or NULL |
| `last_modified` | TIMESTAMP | `LAST-MODIFIED`, UTC-normalised, or NULL |

### `ical_todos(input) → table`

One row per `VTODO`: `uid`, `summary`, `due` (TIMESTAMP), `status`,
`priority` (INT), `percent_complete` (INT).

### Scalars

- `ical_calendar_name(input) → VARCHAR` — `X-WR-CALNAME`, falling back to `PRODID`.
- `ical_event_count(input) → INT` — number of `VEVENT`s (0 for empty/unparsable).
- `is_valid_ical(input) → BOOLEAN` — true when the feed parses cleanly.

### Combining functions

Audit a directory of feeds in one pass. DuckDB's `read_blob()` reads each file
as a BLOB, and the scalars run per row (unparsable files come back `false`,
`NULL`, `0`):

```sql
SELECT parse_filename(filename)              AS file,
       ical.main.is_valid_ical(content)      AS is_valid,
       ical.main.ical_calendar_name(content) AS calendar,
       ical.main.ical_event_count(content)   AS events
FROM read_blob('/cal/*.ics')
ORDER BY file;
```

The same scalars work over a table of stored feed text; cast it to `BLOB`:

```sql
SELECT name,
       ical.main.is_valid_ical(feed::BLOB)      AS is_valid,
       ical.main.ical_calendar_name(feed::BLOB) AS calendar,
       ical.main.ical_event_count(feed::BLOB)   AS events
FROM ical.main.example_calendars
ORDER BY name;
```

Find double-booked meetings by joining a feed's events to themselves on
overlapping `[dtstart, dtend)` ranges (back-to-back events do not count):

```sql
SELECT a.summary AS first_event, b.summary AS second_event,
       greatest(a.dtstart, b.dtstart) AS overlap_start,
       least(a.dtend, b.dtend)        AS overlap_end
FROM ical.main.ical_events('/cal/team.ics') AS a
JOIN ical.main.ical_events('/cal/team.ics') AS b
  ON a.uid < b.uid AND a.dtstart < b.dtend AND b.dtstart < a.dtend
ORDER BY overlap_start, first_event;
```

Count the open to-dos due before each event starts. The `LEFT JOIN` keeps
events with none. iCalendar does not link to-dos to events, so any pairing is by
time:

```sql
SELECT e.summary, count(t.uid) AS open_todos_due_before
FROM ical.main.ical_events('/cal/team.ics') AS e
LEFT JOIN ical.main.ical_todos('/cal/team.ics') AS t
  ON t.due < e.dtstart AND t.status IS DISTINCT FROM 'COMPLETED'
GROUP BY e.summary, e.dtstart
ORDER BY e.dtstart;
```

Arithmetic on the `TIMESTAMP WITH TIME ZONE` columns (such as
`dtstart + INTERVAL 7 DAY`) needs DuckDB's `icu` extension, which the DuckDB CLI
and Python packages load automatically.

## Time-zone & all-day handling

iCalendar date-times come in three flavours (RFC 5545 §3.3.5). Every emitted
`TIMESTAMP` is normalised to **UTC** so values are directly comparable (the column
is a `TIMESTAMP WITH TIME ZONE` anchored at UTC — DuckDB renders it with a `+00`
offset):

| Form | Example | Normalisation |
| --- | --- | --- |
| **UTC** | `20240115T093000Z` | taken as-is |
| **Zoned** (`TZID`) | `TZID=America/New_York:20240115T090000` | iCal4j resolves the `VTIMEZONE`/Olson zone, then we convert the instant to UTC (e.g. → `14:00 UTC` in January) |
| **Floating** (no zone) | `20240115T093000` | wall-clock; anchored in the worker's default zone, then UTC |

A **`DATE`-valued** `DTSTART` (e.g. `DTSTART;VALUE=DATE:20240704`) marks an
**all-day** event: `all_day = true` and the timestamp is **midnight UTC** of that
calendar date.

## Robustness

Real-world feeds bend RFC 5545 constantly. iCal4j is configured
compatibility-relaxed (`RELAXED_UNFOLDING` / `RELAXED_PARSING` /
`RELAXED_VALIDATION` / `OUTLOOK_COMPATIBILITY`). Per-component parse errors are
swallowed (the bad component is skipped, the rest survive), and a feed that fails
to parse at all yields **no rows** / `is_valid_ical = false` / a `NULL` scalar —
never a crashed query. All library logging is bound to **stderr** so the stdout
Arrow-IPC stream stays clean.

## Build & test

```sh
make build       # fat JAR -> build/libs/vgi-ical-<ver>-all.jar
make test-unit   # JUnit (in-process table/scalar drivers)
make test-sql    # shadowJar + fixtures + haybarn-unittest E2E (TRANSPORT=subprocess|http|unix)
make test        # both
```

`make test-sql` needs haybarn **2.0** (`uv tool install --prerelease allow 'haybarn-unittest>=2.0.0rc11,<2.1'`):
the worker speaks VGI protocol `vgi.v3` / 3.3, which haybarn 1.5's `vgi` extension rejects.
It runs the suite through `ci/run-integration.sh`, as CI does: that script rewrites
each `require httpfs` / `require icu` into an explicit install-and-load (run raw,
`haybarn-unittest` skips those files) and fails the run if any file is skipped.

The VGI Java SDK (`farm.query:vgi`, `farm.query:vgirpc`) and iCal4j resolve from
**Maven Central** — the build is fully self-contained (no `mavenLocal`, no sibling
checkout).

## Licensing

- This worker: MIT (see `LICENSE`).
- [iCal4j](https://github.com/ical4j/ical4j): **BSD-3-Clause** (permissive).
  Transitive deps (commons-lang3/-codec, threeten-extra, caffeine, jparsec,
  groovy) are Apache-2.0 / BSD / permissive.
- VGI SDK: per Query Farm.

---

## Authorship & License

Written by [Query.Farm](https://query.farm).

Copyright 2026 Query Farm LLC - https://query.farm

