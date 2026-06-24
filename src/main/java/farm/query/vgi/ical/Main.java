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
            java.util.Map.entry("vgi.description_llm",
                    "Parse iCalendar (.ics) feeds in SQL. Turn a VEVENT stream into rows "
                            + "(uid, summary, UTC-normalised start/end, all-day flag, location, "
                            + "status, organizer, attendees array, recurrence rule) and a VTODO "
                            + "stream into rows (uid, summary, due, status, priority, percent "
                            + "complete); read the calendar display name; count events; and test "
                            + "whether bytes/a path are a well-formed iCalendar feed. Inputs are a "
                            + "VARCHAR file path or a BLOB of .ics bytes. Use for calendar "
                            + "ingestion, scheduling analytics, and feed validation."),
            java.util.Map.entry("vgi.description_md",
                    "# ical\n\niCalendar (.ics) parsing over Apache Arrow, backed by iCal4j "
                            + "(RFC 5545).\n\n"
                            + "Table functions: `ical_events`, `ical_todos`. "
                            + "Scalars: `ical_calendar_name`, `ical_event_count`, "
                            + "`is_valid_ical`.\n\n"
                            + "Inputs accept either a VARCHAR file path or a BLOB of `.ics` bytes."),
            java.util.Map.entry("vgi.author", "Query.Farm"),
            java.util.Map.entry("vgi.copyright",
                    "Copyright 2026 Query Farm LLC - https://query.farm"),
            java.util.Map.entry("vgi.license", "MIT"),
            java.util.Map.entry("vgi.support_contact",
                    "https://github.com/Query-farm/vgi-ical/issues"),
            java.util.Map.entry("vgi.support_policy_url",
                    "https://github.com/Query-farm/vgi-ical/blob/main/README.md"));

    /** Schema-level metadata tags for the single {@code main} schema. */
    private static final java.util.Map<String, String> SCHEMA_TAGS = java.util.Map.of(
            "vgi.description_llm",
            "iCalendar (.ics) parsing functions: ical_events / ical_todos expand a feed into "
                    + "rows, while ical_calendar_name, ical_event_count, and is_valid_ical read "
                    + "calendar metadata, count events, and validate a feed.",
            "vgi.description_md",
            "iCalendar (.ics) parsing functions over Apache Arrow (iCal4j / RFC 5545).");

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
