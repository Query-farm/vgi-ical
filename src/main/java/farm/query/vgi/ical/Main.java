package farm.query.vgi.ical;

import farm.query.vgi.Worker;

/**
 * VGI worker entry point for iCalendar (.ics) parsing via iCal4j.
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * INSTALL vgi FROM community; LOAD vgi;
 * ATTACH 'ical' (TYPE vgi, LOCATION 'java -jar vgi-ical-all.jar');
 * SELECT summary, dtstart FROM ical.ical_events('/cal/team.ics') ORDER BY dtstart;
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_ICAL_GIT_COMMIT") != null
                    ? System.getenv("VGI_ICAL_GIT_COMMIT") : "unknown";

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("ical")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("iCalendar (.ics) events, todos, and metadata parsing (iCal4j)")
                .catalogTags(CATALOG_TAGS)
                .sourceUrl("https://github.com/Query-farm/vgi-ical")
                .schemaComment("main", "iCalendar (.ics) parsing functions: extract events and "
                        + "todos as rows, and read calendar-level metadata (iCal4j).")
                .schemaTags("main", SCHEMA_TAGS)
                .registerTable(new IcalEventsFunction())
                .registerTable(new IcalTodosFunction())
                .registerScalar(new CalendarNameFunction())
                .registerScalar(new EventCountFunction())
                .registerScalar(new IsValidFunction());
    }

    /** Catalog-level metadata tags surfaced to DuckDB and the vgi-lint linter. */
    private static final java.util.Map<String, String> CATALOG_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry("vgi.title", "iCalendar (.ics) Parsing"),
            java.util.Map.entry("vgi.keywords", Meta.keywordsJson(
                    "icalendar, ical, ics, calendar, vevent, vtodo, rfc 5545, ical4j, events, "
                            + "todos, tasks, scheduling, rrule, recurrence, calendar feed")),
            java.util.Map.entry("vgi.doc_llm",
                    "# iCalendar parsing in SQL\n\n"
                            + "Parse iCalendar (`.ics`) feeds directly in SQL, backed by iCal4j "
                            + "(RFC 5545).\n\n"
                            + "- `ical_events` turns a VEVENT stream into rows: uid, summary, "
                            + "description, UTC-normalised start/end, all-day flag, location, "
                            + "status, organizer, attendees array, recurrence rule, sequence.\n"
                            + "- `ical_todos` turns a VTODO stream into rows: uid, summary, due, "
                            + "status, priority, percent complete.\n"
                            + "- `ical_calendar_name`, `ical_event_count`, and `is_valid_ical` read "
                            + "the calendar display name, count events, and validate a feed.\n\n"
                            + "Every function takes the same polymorphic first argument: a VARCHAR "
                            + "file path (the worker opens the file) **or** a BLOB of `.ics` bytes. "
                            + "Bad input never crashes a query — it yields no rows / NULL / false.\n\n"
                            + "Use this worker for calendar ingestion, scheduling analytics, and "
                            + "feed validation."),
            java.util.Map.entry("vgi.doc_md",
                    "# iCalendar (.ics) Parsing in SQL\n\n"
                            + "![iCal4j logo](https://www.ical4j.org/images/logo-48.png)\n\n"
                            + "**Query iCalendar feeds directly in DuckDB SQL** — turn `.ics` "
                            + "calendar files into queryable rows of events, to-dos, and "
                            + "calendar metadata, powered by the battle-tested "
                            + "[iCal4j](https://github.com/ical4j/ical4j) RFC 5545 parser.\n\n"
                            + "This VGI extension brings full iCalendar parsing to your SQL "
                            + "workflow. If you work with calendar exports from Google "
                            + "Calendar, Microsoft Outlook, Apple Calendar, or any "
                            + "RFC 5545-compliant feed, you can load and analyze that data "
                            + "without a separate ETL step. It is built for data engineers, "
                            + "analysts, and developers who want to ingest calendar feeds, run "
                            + "scheduling analytics, deduplicate events, audit recurrence "
                            + "rules, or validate `.ics` files entirely in SQL.\n\n"
                            + "Under the hood the worker wraps "
                            + "[iCal4j](https://www.ical4j.org/), the mature open-source Java "
                            + "library implementing the iCalendar specification "
                            + "([RFC 5545](https://datatracker.ietf.org/doc/html/rfc5545)). "
                            + "Parsing runs with relaxed, Outlook-compatible compatibility "
                            + "hints so messy real-world feeds load cleanly, and every "
                            + "timestamp is normalised to UTC and surfaced as "
                            + "`TIMESTAMP WITH TIME ZONE`. Results stream back to DuckDB over "
                            + "Apache Arrow, so large feeds are processed efficiently and bad "
                            + "or unparsable input never crashes a query — it simply yields no "
                            + "rows, `NULL`, or `false`.\n\n"
                            + "The extension exposes five functions. The table functions "
                            + "`ical_events` and `ical_todos` expand a feed into one row per "
                            + "VEVENT and VTODO respectively — surfacing fields such as uid, "
                            + "summary, description, start/end (or due) timestamps, all-day "
                            + "flag, location, status, organizer, attendees array, recurrence "
                            + "rule (RRULE), priority, and percent-complete. The scalar "
                            + "functions `ical_calendar_name`, `ical_event_count`, and "
                            + "`is_valid_ical` read the calendar display name, count events, "
                            + "and validate whether a feed is well-formed. Every function takes "
                            + "the same polymorphic argument: a VARCHAR file path (the worker "
                            + "opens and reads the file) or a BLOB of raw `.ics` bytes (parsed "
                            + "in place), making it easy to query files on disk or calendar "
                            + "data already stored in a column.\n\n"
                            + "## Functions\n\n"
                            + "| function | kind | returns |\n"
                            + "|---|---|---|\n"
                            + "| `ical_events` | table | one row per VEVENT |\n"
                            + "| `ical_todos` | table | one row per VTODO |\n"
                            + "| `ical_calendar_name` | scalar | calendar display name (VARCHAR) |\n"
                            + "| `ical_event_count` | scalar | VEVENT count (INTEGER) |\n"
                            + "| `is_valid_ical` | scalar | well-formed feed? (BOOLEAN) |\n\n"
                            + "## Learn more\n\n"
                            + "- [iCal4j source repository](https://github.com/ical4j/ical4j)\n"
                            + "- [iCal4j documentation](https://www.ical4j.org/)\n"
                            + "- [RFC 5545 (iCalendar specification)]"
                            + "(https://datatracker.ietf.org/doc/html/rfc5545)"),
            java.util.Map.entry("vgi.author", "Query.Farm"),
            java.util.Map.entry("vgi.copyright",
                    "Copyright 2026 Query Farm LLC - https://query.farm"),
            java.util.Map.entry("vgi.license", "MIT"),
            java.util.Map.entry("vgi.support_contact",
                    "https://github.com/Query-farm/vgi-ical/issues"),
            java.util.Map.entry("vgi.support_policy_url",
                    "https://github.com/Query-farm/vgi-ical/blob/main/README.md"));

    /** Schema-level metadata tags for the single {@code main} schema. */
    private static final java.util.Map<String, String> SCHEMA_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry("vgi.title", "iCalendar Functions (main)"),
            java.util.Map.entry("vgi.keywords", Meta.keywordsJson(
                    "icalendar, ics, calendar, events, todos, ical_events, ical_todos, "
                            + "ical_calendar_name, ical_event_count, is_valid_ical, rrule, "
                            + "scheduling, rfc 5545, ical4j")),
            // VGI123 classifying tags MUST use BARE keys (not vgi.-namespaced).
            java.util.Map.entry("domain", "calendar"),
            java.util.Map.entry("category", "parsing"),
            java.util.Map.entry("topic", "icalendar"),
            // VGI139: no per-schema vgi.source_url — source_url lives on the
            // catalog object only (set via Worker.builder().sourceUrl(...)).
            java.util.Map.entry("vgi.doc_llm",
                    "iCalendar (`.ics`) parsing functions. `ical_events` and `ical_todos` expand "
                            + "a feed into one row per VEVENT / VTODO; `ical_calendar_name`, "
                            + "`ical_event_count`, and `is_valid_ical` read the calendar display "
                            + "name, count events, and validate a feed. Each function takes a "
                            + "VARCHAR path or a BLOB of `.ics` bytes."),
            java.util.Map.entry("vgi.doc_md",
                    "iCalendar (`.ics`) parsing functions over Apache Arrow (iCal4j / RFC 5545): "
                            + "two table functions (`ical_events`, `ical_todos`) and three scalars "
                            + "(`ical_calendar_name`, `ical_event_count`, `is_valid_ical`)."),
            // VGI506: representative, catalog-qualified example queries for the schema,
            // as a JSON array so they are counted (VGI151) and executable as written.
            java.util.Map.entry("vgi.example_queries",
                    "[{\"sql\": \"SELECT summary, location FROM ical.main.ical_events("
                            + Meta.SAMPLE_ICS_BLOB + ") ORDER BY dtstart;\", \"description\": "
                            + "\"List the events in an iCalendar feed.\"},"
                            + "{\"sql\": \"SELECT summary, status FROM ical.main.ical_todos("
                            + Meta.SAMPLE_ICS_BLOB + ") ORDER BY due;\", \"description\": "
                            + "\"List the to-dos in an iCalendar feed.\"},"
                            + "{\"sql\": \"SELECT ical.main.ical_calendar_name("
                            + Meta.SAMPLE_ICS_BLOB + ") AS calendar_name;\", \"description\": "
                            + "\"Read the calendar display name.\"},"
                            + "{\"sql\": \"SELECT ical.main.ical_event_count("
                            + Meta.SAMPLE_ICS_BLOB + ") AS event_count;\", \"description\": "
                            + "\"Count the events in a feed.\"},"
                            + "{\"sql\": \"SELECT ical.main.is_valid_ical("
                            + Meta.SAMPLE_ICS_BLOB + ") AS is_valid;\", \"description\": "
                            + "\"Validate an iCalendar feed.\"}]"));

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
