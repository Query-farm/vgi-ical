package farm.query.vgi.ical;

import farm.query.vgi.catalog.View;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The browsable {@code ical.main.example_calendars} registry view (VGI146).
 *
 * <p>Every function in this worker ({@code ical_events}, {@code ical_todos},
 * {@code ical_calendar_name}, {@code ical_event_count}, {@code is_valid_ical})
 * requires an {@code .ics} feed argument, so an agent has nothing to scan before
 * it knows how to call them. This view is a small, curated, credential-free
 * catalog of ready-to-use sample iCalendar feeds an agent (or human) can
 * {@code SELECT} straight away to see what a well-formed feed looks like, learn
 * the feed's shape, and copy a feed literal into the parsing functions. It is
 * defined purely over an inline {@code VALUES} list, so it scans with no
 * arguments, no network, and no secrets.
 *
 * <p>Precomputed columns ({@code calendar_name}, {@code event_count},
 * {@code todo_count}) let an agent answer questions about the samples without
 * having to paste a full feed into a table function — VGI table functions bind
 * only literal arguments, so a column reference cannot be fed into
 * {@code ical_events(...)} directly.
 */
final class ExampleCalendars {

    private ExampleCalendars() {}

    /**
     * A second self-contained feed (the "Ops Calendar") as a DuckDB {@code VARCHAR}
     * expression: two VEVENTs and one VTODO. Distinct from
     * {@link Meta#SAMPLE_ICS_TEXT_SQL} so the view shows more than one shape.
     */
    static final String OPS_ICS_TEXT_SQL =
            "array_to_string(['BEGIN:VCALENDAR',"
                    + "'VERSION:2.0',"
                    + "'PRODID:-//Query Farm//vgi-ical//EN',"
                    + "'X-WR-CALNAME:Ops Calendar',"
                    + "'BEGIN:VEVENT',"
                    + "'UID:ops-1@query.farm',"
                    + "'SUMMARY:Deploy release',"
                    + "'DTSTART:20240310T150000Z',"
                    + "'DTEND:20240310T160000Z',"
                    + "'STATUS:CONFIRMED',"
                    + "'END:VEVENT',"
                    + "'BEGIN:VEVENT',"
                    + "'UID:ops-2@query.farm',"
                    + "'SUMMARY:Retro',"
                    + "'DTSTART:20240311T170000Z',"
                    + "'DTEND:20240311T173000Z',"
                    + "'STATUS:CONFIRMED',"
                    + "'END:VEVENT',"
                    + "'BEGIN:VTODO',"
                    + "'UID:ops-todo-1@query.farm',"
                    + "'SUMMARY:Update changelog',"
                    + "'DUE:20240312T170000Z',"
                    + "'STATUS:NEEDS-ACTION',"
                    + "'END:VTODO',"
                    + "'END:VCALENDAR'], chr(10))";

    /**
     * Build the {@code example_calendars} view: a SQL view over an inline
     * {@code VALUES} list of curated sample feeds. The {@code feed} column carries
     * the raw, line-folded iCalendar text (the same feeds the examples use); the
     * other columns are precomputed facts about each sample.
     */
    static View view() {
        String definition =
                "SELECT * FROM (VALUES\n"
                        + "  ('team_calendar', 'Team Calendar', 1, 1, "
                        + "'A weekly recurring Sprint Planning event (with two attendees and an "
                        + "RRULE) plus one open release-notes to-do.', "
                        + Meta.SAMPLE_ICS_TEXT_SQL + "),\n"
                        + "  ('ops_calendar', 'Ops Calendar', 2, 1, "
                        + "'Two operations events (a deploy and a retro) and one open changelog "
                        + "to-do.', "
                        + OPS_ICS_TEXT_SQL + ")\n"
                        + ") AS t(name, calendar_name, event_count, todo_count, description, feed)";

        String docLlm =
                "A curated, browsable registry of ready-to-use example iCalendar (`.ics`) feeds, "
                        + "one per row. Query it first to discover what a well-formed feed looks "
                        + "like and to obtain a sample you can study or copy. Columns: `name` (a "
                        + "short slug id), `calendar_name` (the feed's X-WR-CALNAME display name), "
                        + "`event_count` (how many VEVENTs the sample has), `todo_count` (how many "
                        + "VTODOs it has), `description` (a one-line summary), and `feed` (the raw "
                        + "iCalendar text). The view takes no arguments and needs no credentials. "
                        + "Note: VGI table functions accept only literal arguments, so paste the "
                        + "`feed` text inline into `ical_events`, `ical_todos`, `ical_event_count`, "
                        + "`ical_calendar_name`, or `is_valid_ical` rather than passing this column "
                        + "as a subquery.";

        String docMd =
                "## example_calendars\n\n"
                        + "A small, built-in catalog of sample **iCalendar** (`.ics`) feeds you can "
                        + "query without supplying any input. It exists so you can see a "
                        + "well-formed feed and the kinds of calendar this worker understands "
                        + "before calling the parsing functions, which all require a feed "
                        + "argument.\n\n"
                        + "Each row describes one sample: a slug `name`, the `calendar_name` "
                        + "display name (X-WR-CALNAME), a precomputed `event_count` and "
                        + "`todo_count`, a short `description`, and the raw `feed` text itself. "
                        + "Browse it, sort by `event_count`, or copy a `feed` literal into "
                        + "`ical_events`, `ical_todos`, or the scalar helpers to experiment.\n\n"
                        + "The view is defined over an inline `VALUES` list, so it scans instantly "
                        + "with no network access and no credentials.";

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", "Example iCalendar Feeds");
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", Meta.keywordsJson(
                "example, examples, sample, samples, registry, catalog, icalendar, ics, "
                        + "calendar, feed, vevent, vtodo, browse, demo"));
        tags.put("vgi.category", "Calendar metadata");
        // VGI123 classifying tags — BARE keys, reusing the schema's own vocabulary.
        tags.put("domain", "calendar");
        tags.put("category", "parsing");
        tags.put("topic", "icalendar");
        // VGI511: an object-level example that references the view by name.
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT name, calendar_name, event_count, todo_count "
                        + "FROM ical.main.example_calendars ORDER BY event_count DESC, name;\", "
                        + "\"description\": \"List the built-in sample iCalendar feeds, richest "
                        + "first, with their display name and event/to-do counts.\"}]");

        Map<String, String> columnComments = new LinkedHashMap<>();
        columnComments.put("name", "Short slug identifier for the sample feed (e.g. `team_calendar`).");
        columnComments.put("calendar_name",
                "The feed's display name from its X-WR-CALNAME property (e.g. `Team Calendar`).");
        columnComments.put("event_count",
                "Number of VEVENT entries in the sample feed (a small integer count).");
        columnComments.put("todo_count",
                "Number of VTODO entries in the sample feed (a small integer count).");
        columnComments.put("description", "One-line human summary of what the sample feed contains.");
        columnComments.put("feed", "Raw, line-folded iCalendar (`.ics`) text for the sample feed.");

        return new View(
                        "main",
                        "example_calendars",
                        definition,
                        "Curated, browsable registry of ready-to-use sample iCalendar (.ics) feeds.",
                        tags)
                .withColumnComments(columnComments);
    }
}
