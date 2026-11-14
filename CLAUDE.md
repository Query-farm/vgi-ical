# CLAUDE.md — vgi-ical

Contributor/agent notes. User-facing docs live in `README.md`; this is the
"how it's built and where the sharp edges are" companion.

## What this is

A [VGI](https://query.farm) worker (Java) wrapping **iCal4j** to parse iCalendar
(`.ics`) feeds — events, todos, calendar metadata — into DuckDB SQL rows. Built
with Gradle (Kotlin DSL, JDK 21) into a shaded fat JAR. Catalog name `ical`
(single `main` schema). Modeled on the sibling workers `vgi-tika`, `vgi-poi`,
`vgi-grammar` (all resolve the SDK from Maven Central).

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Gradle, shadow plugin (com.gradleup.shadow 9.6.1)
src/main/java/farm/query/vgi/ical/
  Main.java                  Worker.builder().catalogName("ical")...registerTable/registerScalar
  IcalEventsFunction.java     table fn: ical_events(path|bytes) -> one row per VEVENT
  IcalTodosFunction.java      table fn: ical_todos(path|bytes) -> one row per VTODO
  CalendarNameFunction.java   scalar: ical_calendar_name (X-WR-CALNAME / PRODID)
  EventCountFunction.java     scalar: ical_event_count
  IsValidFunction.java        scalar: is_valid_ical
  IcalEngine.java             the iCal4j integration: parse, TZ->UTC, all-day, error capture
  IcalSchemas.java            Arrow schemas + cell writers (TIMESTAMP/UTC, VARCHAR[])
  InputType.java              VARCHAR path / BLOB bytes overload specs and argument docs
  DocInput.java               path-vs-bytes input dispatch; rejects unsupported types
  ScalarInput.java            per-cell path/bytes resolution for typed scalar overloads
  ExampleCalendars.java       view: example_calendars (browsable sample feeds, VGI146)
  ComposedExamples.java       multi-object example SQL, defined once, referenced by every list
  Meta.java                   shared tag helpers + the SAMPLE_ICS inline feed literal
src/test/java/...            JUnit: IcalEvents, IcalTodos, ScalarFunctions, DocInput,
                             ExampleMetadata (example-list JSON/cap/placement)
                             + Fixtures.java (.ics text builder) + SqlFixtureGenerator + TestSupport
test/sql/*.test + data/      haybarn-unittest E2E + committed generated fixtures (*.ics)
Makefile                     build / fixtures / test-unit / test-sql / test / clean
```

## Sharp edges

1. **iCal4j 4.x date model.** `DateProperty.getDate()` returns a
   `java.time.temporal.Temporal` — one of `LocalDate` (all-day),
   `LocalDateTime` (floating), `OffsetDateTime`/`ZonedDateTime`/`Instant`
   (zoned/UTC). `IcalEngine.toInstant()` switches on the concrete type to
   produce a single UTC `Instant`; a `LocalDate` is treated as all-day (midnight
   UTC) and flips `all_day = true`. The 4.x API has no `getProperty(String)` on
   `Component`/`Calendar` — iterate `getProperties()` and match `getName()`
   case-insensitively; `getComponents()` is bounded to `CalendarComponent`.
2. **TIMESTAMP is UTC-tagged.** The Arrow type is `timestampMicros("UTC")`, so
   DuckDB surfaces it as `TIMESTAMP WITH TIME ZONE` and renders `+00`. The
   `.test` expected values therefore carry the `+00` suffix. The backing Arrow
   vector is `TimeStampMicroTZVector`; we write via the `TimeStampVector` base
   (`setSafe(int,long)` epoch-micros).
3. **Logging must not touch stdout.** A stdio VGI worker speaks Arrow-IPC on
   stdout; any library log line on stdout corrupts the transport and hangs the
   worker. iCal4j + caffeine log via slf4j → we bind `slf4j-simple` (all output
   to stderr).
4. **Compatibility-relaxed parsing.** `CompatibilityHints` enables relaxed
   unfolding/parsing/validation + Outlook compat in a static initializer so
   real-world feeds don't throw on minor RFC violations. Per-component failures
   are swallowed; a wholly-unparsable feed yields no rows / `is_valid_ical=false`
   / NULL — never a crash.
5. **`Add-Opens: java.base/java.nio`** is baked into the fat-JAR manifest (Arrow
   off-heap needs it) so a bare `java -jar vgi-ical-all.jar` works as a LOCATION.
6. **`haybarn-unittest` skips `require vgi`** — `.test` files use explicit
   `LOAD vgi;` then `ATTACH ... (TYPE vgi, LOCATION '{VGI_ICAL_WORKER}')`
   (haybarn 2.0 syntax; `${...}` is deprecated). It also skips `require httpfs`
   / `require icu`, so always run the suite through `ci/run-integration.sh`
   (`make test-sql` does), which rewrites those into INSTALL+LOAD and fails on
   any skipped file.
7. **Table functions bind only literal arguments.** `ical_events(t.feed)`,
   LATERAL calls and subquery arguments are Binder Errors; the scalars accept
   columns. A VARCHAR argument is always a path, so feed *text* must be cast
   `::BLOB`.
8. **Composed examples** live in `ComposedExamples.java` and are referenced from
   the schema-level `vgi.example_queries` and from the driving object's list,
   within its first 5 entries (the vgi-lint agent simulator shows only those).
   VGI417 caps each example list at 8,000 characters; the schema list is close,
   and `ExampleMetadataTest` enforces the cap and the placement.
9. **TIMESTAMPTZ arithmetic needs `icu`.** `dtstart + INTERVAL ...` fails without
   it (bare haybarn-unittest), so `composed.test` does `require icu` and
   `SET TimeZone = 'UTC'`.

## vgi 0.48.0 record arity (test drivers)

**Protocol:** vgi 0.48.0 speaks VGI protocol `vgi.v3` / 3.3.0, which only the
haybarn **2.0** community `vgi` extension attaches (haybarn 1.5 rejects it).
`haybarn-unittest` 2.0 isolates `$HOME`, so `ci/run-integration.sh` and
`make test-sql` pass `--keep-home` (otherwise the installed `vgi` extension is
"not found").


`TestSupport.invoke` builds a `TableInitParams` with the full vgi 0.48.0
component list (25), including `bindOutputSchema` and the trailing `atUnit`/`atValue`/
`storage`/`copyFrom`/`splitPayloads`. If the SDK bumps and the constructor
arity changes, `javap -cp <vgi jar>
farm.query.vgi.table.TableInitParams` shows the current signature.

## Verify

```sh
export PATH="$HOME/.local/bin:$PATH"
./gradlew test     # JUnit
make test-sql      # E2E (shadowJar + fixtures + ci/run-integration.sh)
uvx --refresh --prerelease allow --from 'vgi-lint-check>=2.5.1' \
  vgi-lint lint --execute --execute-mode run --fail-on info   # reads vgi-lint.toml
```
