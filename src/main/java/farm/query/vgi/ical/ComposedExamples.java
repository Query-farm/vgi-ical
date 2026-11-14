package farm.query.vgi.ical;

/**
 * Example queries that combine two or more of this worker's own objects in one
 * query. Each SQL string is defined once here and referenced from every
 * {@code vgi.example_queries} / {@code vgi.executable_examples} list that shows
 * it: the schema-level list, and the per-object list of the object that drives
 * the query (the vgi-lint agent simulator only shows per-object examples, so a
 * composed example must sit within the first five of its driving object).
 *
 * <p>VGI table functions bind only literal arguments (a column or subquery
 * argument is a Binder Error), so composition here takes two shapes: scalars
 * applied to a column of stored feeds, and joins between table-function calls
 * over the same literal feed.
 */
final class ComposedExamples {

    private ComposedExamples() {}

    /**
     * A small "Design Calendar" feed as a DuckDB {@code BLOB} expression: three
     * events on one morning. Design review (09:00-10:00Z) and Customer call
     * (09:30-10:15Z) overlap; Team sync (10:15-11:00Z) starts exactly when the
     * Customer call ends, so it is back-to-back rather than a conflict.
     */
    static final String DESIGN_ICS_BLOB =
            "array_to_string(['BEGIN:VCALENDAR',"
                    + "'VERSION:2.0',"
                    + "'PRODID:-//Query Farm//vgi-ical//EN',"
                    + "'X-WR-CALNAME:Design Calendar',"
                    + "'BEGIN:VEVENT','UID:d1@query.farm','SUMMARY:Design review',"
                    + "'DTSTART:20240402T090000Z','DTEND:20240402T100000Z','END:VEVENT',"
                    + "'BEGIN:VEVENT','UID:d2@query.farm','SUMMARY:Customer call',"
                    + "'DTSTART:20240402T093000Z','DTEND:20240402T101500Z','END:VEVENT',"
                    + "'BEGIN:VEVENT','UID:d3@query.farm','SUMMARY:Team sync',"
                    + "'DTSTART:20240402T101500Z','DTEND:20240402T110000Z','END:VEVENT',"
                    + "'END:VCALENDAR'], chr(10))::BLOB";

    /** example_calendars view + is_valid_ical + ical_calendar_name + ical_event_count. */
    static final String FEED_AUDIT_SQL =
            "SELECT name, ical.main.is_valid_ical(feed::BLOB) AS is_valid, "
                    + "ical.main.ical_calendar_name(feed::BLOB) AS parsed_name, "
                    + "ical.main.ical_event_count(feed::BLOB) AS parsed_events "
                    + "FROM (SELECT name, feed FROM ical.main.example_calendars "
                    + "UNION ALL SELECT 'truncated_export', 'BEGIN:VCALENDAR') AS f "
                    + "ORDER BY name";

    static final String FEED_AUDIT_DESCRIPTION =
            "Health-check a column of stored feeds: the scalars accept columns (table "
                    + "functions do not). Both example_calendars samples come back valid (Ops "
                    + "Calendar 2 events, Team Calendar 1); a truncated export comes back false, "
                    + "NULL, 0. Cast text to `BLOB`: a `VARCHAR` is read as a file path.";

    /** ical_events self-join: pairs of events whose time ranges overlap. */
    static final String EVENT_CONFLICTS_SQL =
            "SELECT a.summary AS first_event, b.summary AS second_event, "
                    + "greatest(a.dtstart, b.dtstart) AS overlap_start, "
                    + "least(a.dtend, b.dtend) AS overlap_end "
                    + "FROM ical.main.ical_events(" + DESIGN_ICS_BLOB + ") AS a "
                    + "JOIN ical.main.ical_events(" + DESIGN_ICS_BLOB + ") AS b "
                    + "ON a.uid < b.uid AND a.dtstart < b.dtend AND b.dtstart < a.dtend "
                    + "ORDER BY overlap_start, first_event";

    static final String EVENT_CONFLICTS_DESCRIPTION =
            "Find double-booked events: self-join ical_events on overlapping [dtstart, dtend) "
                    + "ranges. Returns Design review with Customer call, overlapping 09:30-10:00 "
                    + "UTC; Team sync starts as Customer call ends, so it is not a conflict.";

    /** The Ops Calendar sample (two events, one to-do) as a {@code BLOB}. */
    private static final String OPS_ICS_BLOB = ExampleCalendars.OPS_ICS_TEXT_SQL + "::BLOB";

    /** ical_events JOIN ical_todos on a time window. */
    static final String TODOS_DUE_WEEK_SQL =
            "SELECT e.summary AS event, e.dtstart, t.summary AS todo, t.due "
                    + "FROM ical.main.ical_events(" + OPS_ICS_BLOB + ") AS e "
                    + "JOIN ical.main.ical_todos(" + OPS_ICS_BLOB + ") AS t "
                    + "ON t.due BETWEEN e.dtstart AND e.dtstart + INTERVAL 7 DAY "
                    + "ORDER BY e.dtstart, t.due";

    static final String TODOS_DUE_WEEK_DESCRIPTION =
            "List, for each event, the to-dos that fall due within 7 days of its start, by "
                    + "joining ical_events to ical_todos on a time window. iCalendar does not link "
                    + "to-dos to events, so the pairing is by time only: the Ops Calendar's one "
                    + "to-do, Update changelog (due 2024-03-12 17:00 UTC), pairs with both Deploy "
                    + "release and Retro.";

    static final String FEED_AUDIT_JSON = entry(FEED_AUDIT_SQL, FEED_AUDIT_DESCRIPTION);
    static final String EVENT_CONFLICTS_JSON =
            entry(EVENT_CONFLICTS_SQL, EVENT_CONFLICTS_DESCRIPTION);
    static final String TODOS_DUE_WEEK_JSON = entry(TODOS_DUE_WEEK_SQL, TODOS_DUE_WEEK_DESCRIPTION);

    /** {@link #TODOS_DUE_WEEK_SQL} as a {@code vgi.executable_examples} entry. */
    static final String TODOS_DUE_WEEK_EXECUTABLE_JSON = "{\"description\": \""
            + TODOS_DUE_WEEK_DESCRIPTION + "\", \"sql\": \"" + TODOS_DUE_WEEK_SQL + "\"}";

    /** One {@code {"sql", "description"}} entry, in the style the example lists use. */
    private static String entry(String sql, String description) {
        return "{\"sql\": \"" + sql + ";\", \"description\": \"" + description + "\"}";
    }
}
