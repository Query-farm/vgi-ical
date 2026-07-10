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
                .registerScalar(new IsValidFunction())
                // VGI146: a browsable, credential-free registry view so an agent can
                // scan real sample feeds before calling the feed-argument functions.
                .registerView(ExampleCalendars.view());
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
                            + "This worker turns calendar data — the `.ics` exports produced by "
                            + "Google Calendar, Microsoft Outlook, Apple Calendar, and any "
                            + "RFC 5545 feed — into queryable rows and scalar values, so you can "
                            + "run scheduling analytics, deduplicate or audit events, inspect "
                            + "recurrence rules, and check feed health without a separate ETL "
                            + "step.\n\n"
                            + "**Key concepts.** Every function accepts the same polymorphic first "
                            + "argument: a VARCHAR filesystem path (the worker opens and reads the "
                            + "file) **or** a BLOB of raw `.ics` bytes (parsed in place), so you "
                            + "can work over files on disk or calendar text already stored in a "
                            + "column. Timestamps are normalised to UTC and surfaced as "
                            + "`TIMESTAMP WITH TIME ZONE`. Parsing is compatibility-relaxed "
                            + "(Outlook-friendly) and bad or NULL input never crashes a query — it "
                            + "yields no rows, NULL, or false.\n\n"
                            + "**When to use it.** Reach for this worker whenever you need "
                            + "calendar feeds as data: ingesting `.ics` exports, building "
                            + "scheduling or task reports, or validating a feed before downstream "
                            + "processing. List the `main` schema to discover the available "
                            + "functions and their columns."),
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
                            + "## What you can do\n\n"
                            + "The worker exposes both **table functions**, which expand a feed "
                            + "into one row per calendar entry (surfacing fields such as uid, "
                            + "summary, description, start/end or due timestamps, all-day flag, "
                            + "location, status, organizer, attendees, recurrence rule, priority, "
                            + "and percent-complete), and **scalar functions** for quick, "
                            + "row-level answers such as reading a calendar's display name, "
                            + "tallying entries, or validating a feed.\n\n"
                            + "Every function takes the same polymorphic argument: a VARCHAR file "
                            + "path (the worker opens and reads the file) or a BLOB of raw `.ics` "
                            + "bytes (parsed in place), making it easy to query files on disk or "
                            + "calendar data already stored in a column. Attach the worker and "
                            + "list the `main` schema to discover the exact functions, their "
                            + "arguments, and their result columns.\n\n"
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
                    "https://github.com/Query-farm/vgi-ical/blob/main/README.md"),
            // VGI152/VGI920: a fixed analyst-task suite for `vgi-lint simulate`. Each
            // task grades the analyst's SQL result against a deterministic
            // reference_sql over an inline .ics feed, with value-only grading
            // (ignore_column_names + unordered) so it is stable.
            java.util.Map.entry("vgi.agent_test_tasks", agentTestTasksJson()));

    /**
     * A small, deterministic iCalendar feed described to the simulated analyst in
     * each {@code vgi.agent_test_tasks} prompt, so the task is self-contained. The
     * matching {@link #AGENT_ICS_BLOB} builds the same feed as a DuckDB {@code BLOB}
     * for the reference query.
     */
    private static final String AGENT_ICS_TEXT =
            "BEGIN:VCALENDAR\n"
                    + "VERSION:2.0\n"
                    + "PRODID:-//Query Farm//vgi-ical//EN\n"
                    + "X-WR-CALNAME:Ops Calendar\n"
                    + "BEGIN:VEVENT\n"
                    + "UID:a1@query.farm\n"
                    + "SUMMARY:Deploy release\n"
                    + "DTSTART:20240310T150000Z\n"
                    + "DTEND:20240310T160000Z\n"
                    + "END:VEVENT\n"
                    + "BEGIN:VEVENT\n"
                    + "UID:a2@query.farm\n"
                    + "SUMMARY:Retro\n"
                    + "DTSTART:20240311T170000Z\n"
                    + "DTEND:20240311T173000Z\n"
                    + "END:VEVENT\n"
                    + "BEGIN:VTODO\n"
                    + "UID:b1@query.farm\n"
                    + "SUMMARY:Update changelog\n"
                    + "DUE:20240312T170000Z\n"
                    + "STATUS:NEEDS-ACTION\n"
                    + "END:VTODO\n"
                    + "END:VCALENDAR";

    /** The {@link #AGENT_ICS_TEXT} feed as a DuckDB {@code BLOB} expression. */
    private static final String AGENT_ICS_BLOB =
            "array_to_string(['BEGIN:VCALENDAR',"
                    + "'VERSION:2.0',"
                    + "'PRODID:-//Query Farm//vgi-ical//EN',"
                    + "'X-WR-CALNAME:Ops Calendar',"
                    + "'BEGIN:VEVENT',"
                    + "'UID:a1@query.farm',"
                    + "'SUMMARY:Deploy release',"
                    + "'DTSTART:20240310T150000Z',"
                    + "'DTEND:20240310T160000Z',"
                    + "'END:VEVENT',"
                    + "'BEGIN:VEVENT',"
                    + "'UID:a2@query.farm',"
                    + "'SUMMARY:Retro',"
                    + "'DTSTART:20240311T170000Z',"
                    + "'DTEND:20240311T173000Z',"
                    + "'END:VEVENT',"
                    + "'BEGIN:VTODO',"
                    + "'UID:b1@query.farm',"
                    + "'SUMMARY:Update changelog',"
                    + "'DUE:20240312T170000Z',"
                    + "'STATUS:NEEDS-ACTION',"
                    + "'END:VTODO',"
                    + "'END:VCALENDAR'], chr(10))::BLOB";

    /**
     * Build the {@code vgi.agent_test_tasks} JSON suite (VGI152/VGI920). Each task
     * gives the analyst only a {@code prompt} (with the feed spelled out); grading
     * compares the analyst's result to a deterministic {@code reference_sql} over
     * {@link #AGENT_ICS_BLOB}, value-only (column names + row order ignored).
     */
    private static String agentTestTasksJson() {
        java.util.List<java.util.Map<String, Object>> tasks = new java.util.ArrayList<>();
        tasks.add(agentTask(
                "count_events",
                "You have this iCalendar (.ics) feed:\n\n" + AGENT_ICS_TEXT + "\n\n"
                        + "Using this worker, count how many events (VEVENT entries) the feed "
                        + "contains. Return the single count.",
                "SELECT ical.main.ical_event_count(" + AGENT_ICS_BLOB + ")",
                "The feed has two VEVENTs, so the count is 2."));
        tasks.add(agentTask(
                "calendar_display_name",
                "You have this iCalendar (.ics) feed:\n\n" + AGENT_ICS_TEXT + "\n\n"
                        + "Using this worker, read the calendar's display name (the "
                        + "X-WR-CALNAME property). Return the single name.",
                "SELECT ical.main.ical_calendar_name(" + AGENT_ICS_BLOB + ")",
                "The X-WR-CALNAME is 'Ops Calendar'."));
        tasks.add(agentTask(
                "validate_feed",
                "You have this iCalendar (.ics) feed:\n\n" + AGENT_ICS_TEXT + "\n\n"
                        + "Using this worker, determine whether it is a well-formed iCalendar "
                        + "feed. Return the single boolean.",
                "SELECT ical.main.is_valid_ical(" + AGENT_ICS_BLOB + ")",
                "The feed parses cleanly, so the result is true."));
        tasks.add(agentTask(
                "list_event_summaries",
                "You have this iCalendar (.ics) feed:\n\n" + AGENT_ICS_TEXT + "\n\n"
                        + "Using this worker, list the SUMMARY of every event in the feed. "
                        + "Return one summary per row.",
                "SELECT summary FROM ical.main.ical_events(" + AGENT_ICS_BLOB
                        + ") ORDER BY dtstart",
                "The two event summaries are 'Deploy release' and 'Retro'."));
        tasks.add(agentTask(
                "list_open_todos",
                "You have this iCalendar (.ics) feed:\n\n" + AGENT_ICS_TEXT + "\n\n"
                        + "Using this worker, list the SUMMARY of every to-do (VTODO) in the "
                        + "feed. Return one summary per row.",
                "SELECT summary FROM ical.main.ical_todos(" + AGENT_ICS_BLOB + ")",
                "The single to-do summary is 'Update changelog'."));
        // VGI520: exercise the browsable example_calendars registry view. The
        // reference reads precomputed columns straight off the view (no feed
        // argument needed), so grading is deterministic.
        tasks.add(agentTask(
                "browse_example_calendars",
                "This worker ships a built-in registry of sample iCalendar feeds. Using the "
                        + "worker, find which built-in sample calendar has the most events, and "
                        + "return that calendar's display name.",
                "SELECT calendar_name FROM ical.main.example_calendars "
                        + "ORDER BY event_count DESC, name LIMIT 1",
                "The 'Ops Calendar' sample has the most events (two), so its display name is "
                        + "'Ops Calendar'."));
        return tasksToJson(tasks);
    }

    private static java.util.Map<String, Object> agentTask(
            String name, String prompt, String referenceSql, String successCriteria) {
        java.util.Map<String, Object> t = new java.util.LinkedHashMap<>();
        t.put("name", name);
        t.put("prompt", prompt);
        t.put("reference_sql", referenceSql);
        t.put("success_criteria", successCriteria);
        // Grade on VALUES only — every reference returns a single column, so
        // ignoring column aliases and row order keeps grading stable.
        t.put("ignore_column_names", true);
        t.put("unordered", true);
        return t;
    }

    /** Minimal JSON serialization for the agent-test-task list (strings + booleans). */
    private static String tasksToJson(java.util.List<java.util.Map<String, Object>> tasks) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{');
            boolean firstKey = true;
            for (java.util.Map.Entry<String, Object> e : tasks.get(i).entrySet()) {
                if (!firstKey) {
                    sb.append(',');
                }
                firstKey = false;
                sb.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Boolean) {
                    sb.append(v.toString());
                } else {
                    sb.append('"').append(jsonEscape(v.toString())).append('"');
                }
            }
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

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
            // VGI413: an ordered category registry; each function carries a matching
            // vgi.category tag so the schema renders as navigable sections.
            java.util.Map.entry("vgi.categories",
                    "[{\"name\": \"Events\", \"description\": \"Extract calendar events "
                            + "(VEVENT) from a feed as rows, or count them.\"},"
                            + "{\"name\": \"To-dos\", \"description\": \"Extract calendar tasks "
                            + "(VTODO) from a feed as rows.\"},"
                            + "{\"name\": \"Calendar metadata\", \"description\": \"Read "
                            + "calendar-level properties and validate whether a feed is "
                            + "well-formed.\"}]"),
            java.util.Map.entry("vgi.doc_llm",
                    "iCalendar (`.ics`) parsing functions backed by iCal4j (RFC 5545). This "
                            + "schema turns calendar feeds into queryable rows and scalar values "
                            + "for scheduling analytics, task tracking, and feed validation.\n\n"
                            + "Every function accepts the same polymorphic first argument — a "
                            + "VARCHAR file path or a BLOB of raw `.ics` bytes — and normalises "
                            + "timestamps to UTC. Bad or NULL input yields no rows, NULL, or "
                            + "false rather than an error."),
            java.util.Map.entry("vgi.doc_md",
                    "# iCalendar functions\n\n"
                            + "Parse iCalendar (`.ics`) feeds directly in SQL over Apache Arrow, "
                            + "powered by [iCal4j](https://www.ical4j.org/) "
                            + "([RFC 5545](https://datatracker.ietf.org/doc/html/rfc5545)).\n\n"
                            + "The functions in this schema cover three areas:\n\n"
                            + "- **Events** — expand a feed into event rows, or tally them.\n"
                            + "- **To-dos** — expand a feed into task rows.\n"
                            + "- **Calendar metadata** — read calendar-level properties and "
                            + "validate feed health.\n\n"
                            + "Every function accepts the same polymorphic argument: a VARCHAR "
                            + "file path (the worker reads the file) or a BLOB of raw `.ics` "
                            + "bytes (parsed in place). Timestamps are normalised to UTC and "
                            + "surfaced as `TIMESTAMP WITH TIME ZONE`, and bad or NULL input "
                            + "never crashes a query."),
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
