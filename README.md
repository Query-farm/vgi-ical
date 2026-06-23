# vgi-ical

[![test](https://github.com/Query-farm/vgi-ical/actions/workflows/test.yml/badge.svg)](https://github.com/Query-farm/vgi-ical/actions/workflows/test.yml)

A [VGI](https://query.farm) worker that brings [iCal4j](https://www.ical4j.org/)
into DuckDB/SQL: parse iCalendar (`.ics`) feeds — **events, todos, and calendar
metadata** — into relational rows, as SQL table and scalar functions.

Written in **Java** because iCal4j is the mature, RFC 5545–complete iCalendar
stack on the JVM (VTIMEZONE resolution, recurrence rules, compatibility-relaxed
parsing for real-world Google/Outlook/Apple exports) with no equal elsewhere.

```sql
INSTALL vgi FROM community; LOAD vgi;
ATTACH 'ical' (TYPE vgi, LOCATION 'java -jar /path/to/vgi-ical-all.jar');

-- one calendar file -> one row per event, sorted by start time
SELECT summary, dtstart, location
FROM ical.ical_events('/cal/team.ics')
ORDER BY dtstart;

-- a column of feeds (paths or .ics bytes) work too — pass a BLOB
SELECT summary FROM ical.ical_events((SELECT body FROM feeds LIMIT 1));
```

## How iCalendar maps onto SQL

| Area | SQL surface | VGI primitive |
| --- | --- | --- |
| **Events** | `SELECT * FROM ical.ical_events(path \| bytes)` | table function (1 feed → N event rows) |
| **Todos** | `SELECT * FROM ical.ical_todos(path \| bytes)` | table function (1 feed → N todo rows) |
| **Calendar name** | `ical.ical_calendar_name(path \| bytes)` | scalar (VARCHAR) |
| **Event count** | `ical.ical_event_count(path \| bytes)` | scalar (INT) |
| **Validity** | `ical.is_valid_ical(path \| bytes)` | scalar (BOOLEAN) |

### Path-or-bytes input

Every function takes a single polymorphic argument: a **VARCHAR path** (the worker
opens the `.ics` file) or a **BLOB** of `.ics` bytes that travelled over Arrow.
A `NULL` argument yields `NULL` / no rows.

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

### `ical_todos(input) → table`

One row per `VTODO`: `uid`, `summary`, `due` (TIMESTAMP), `status`,
`priority` (INT), `percent_complete` (INT).

### Scalars

- `ical_calendar_name(input) → VARCHAR` — `X-WR-CALNAME`, falling back to `PRODID`.
- `ical_event_count(input) → INT` — number of `VEVENT`s (0 for empty/unparsable).
- `is_valid_ical(input) → BOOLEAN` — true when the feed parses cleanly.

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
make test-sql    # shadowJar + fixtures + haybarn-unittest E2E
make test        # both
```

The VGI Java SDK (`farm.query:vgi`, `farm.query:vgirpc`) and iCal4j resolve from
**Maven Central** — the build is fully self-contained (no `mavenLocal`, no sibling
checkout).

## Licensing

- This worker: MIT (see `LICENSE`).
- [iCal4j](https://github.com/ical4j/ical4j): **BSD-3-Clause** (permissive).
  Transitive deps (commons-lang3/-codec, threeten-extra, caffeine, jparsec,
  groovy) are Apache-2.0 / BSD / permissive.
- VGI SDK: per Query Farm.
