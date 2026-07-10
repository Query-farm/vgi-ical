package farm.query.vgi.ical;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile (0.26.0) expects on <em>every</em> function
 * and table.
 *
 * <p>Each object surfaces these tags via {@code FunctionMetadata.withTags(...)}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name (must not
 *       normalize-equal the machine name).</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — a Markdown narrative aimed at an
 *       LLM/agent audience.</li>
 *   <li>{@code vgi.doc_md} (VGI113) — a Markdown narrative for human docs
 *       (distinct content from {@code vgi.doc_llm}).</li>
 *   <li>{@code vgi.keywords} (VGI126/VGI138) — search terms/synonyms as a JSON
 *       array of strings.</li>
 * </ul>
 *
 * <p>Per-object {@code vgi.source_url} is intentionally NOT set (VGI139): the
 * {@code source_url} lives on the catalog object only.
 */
final class Meta {

    private Meta() {}

    /**
     * Per-argument documentation (VGI312) for the single polymorphic {@code input}
     * argument shared by every function in this worker. The argument is an
     * any-typed positional: pass either a VARCHAR file path (the worker opens and
     * reads the file) or a BLOB of raw {@code .ics} bytes (parsed in place). A
     * NULL argument is handled gracefully — table functions emit no rows and
     * scalars return NULL — so the worker never errors on missing input.
     */
    static final String INPUT_ARG_DOC =
            "The iCalendar (.ics) feed to read. Pass either a filesystem path — the "
                    + "worker opens and reads that file — or the raw .ics content itself, "
                    + "which is parsed in place (e.g. an inline feed or a column of fetched "
                    + "calendar data). NULL or unparsable input is handled gracefully: table "
                    + "functions return no rows and scalar functions return NULL/0/false "
                    + "rather than raising an error.";

    /**
     * Render a comma-separated keyword list as a JSON array of strings, e.g.
     * {@code "a, b"} -> {@code ["a","b"]}. {@code vgi.keywords} must be a JSON
     * array (VGI138), not a comma-separated string.
     */
    static String keywordsJson(String commaSeparated) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String raw : commaSeparated.split(",")) {
            String kw = raw.trim();
            if (kw.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(kw.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /**
     * Build the four standard per-object discovery/description tags.
     *
     * <p>{@code vgi.keywords} is emitted as a JSON array of strings (VGI138). No
     * per-object {@code vgi.source_url} is set: VGI139 keeps {@code source_url}
     * on the catalog object only, so {@code javaFileName} is accepted for call-site
     * documentation but no longer surfaced as a tag.
     *
     * @param title        human display name (VGI124)
     * @param docLlm       Markdown narrative for an LLM/agent audience (VGI112)
     * @param docMd        Markdown narrative for human docs (VGI113), distinct from {@code docLlm}
     * @param keywords     comma-separated search terms, serialized to a JSON array (VGI126/VGI138)
     * @param javaFileName implementing source file, e.g. {@code "IcalEventsFunction.java"}
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String javaFileName) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywordsJson(keywords));
        return tags;
    }

    /**
     * A self-contained iCalendar feed expressed as a DuckDB SQL {@code VARCHAR}
     * expression (lines joined with {@code chr(10)}). The feed has one VEVENT
     * (with two attendees and an RRULE) and one VTODO. {@link #SAMPLE_ICS_BLOB}
     * casts this to {@code BLOB} so the worker resolves it as raw {@code .ics}
     * bytes rather than a file path; the raw text form backs the browsable
     * {@code example_calendars} view.
     */
    static final String SAMPLE_ICS_TEXT_SQL =
            "array_to_string(['BEGIN:VCALENDAR',"
                    + "'VERSION:2.0',"
                    + "'PRODID:-//Query Farm//vgi-ical//EN',"
                    + "'X-WR-CALNAME:Team Calendar',"
                    + "'BEGIN:VEVENT',"
                    + "'UID:event-1@query.farm',"
                    + "'SUMMARY:Sprint Planning',"
                    + "'DESCRIPTION:Plan the next two-week sprint.',"
                    + "'DTSTART:20240115T093000Z',"
                    + "'DTEND:20240115T103000Z',"
                    + "'LOCATION:Conference Room A',"
                    + "'STATUS:CONFIRMED',"
                    + "'ORGANIZER:mailto:alice@query.farm',"
                    + "'ATTENDEE:mailto:bob@query.farm',"
                    + "'ATTENDEE:mailto:carol@query.farm',"
                    + "'RRULE:FREQ=WEEKLY;BYDAY=MO',"
                    + "'SEQUENCE:3',"
                    + "'END:VEVENT',"
                    + "'BEGIN:VTODO',"
                    + "'UID:todo-1@query.farm',"
                    + "'SUMMARY:Write release notes',"
                    + "'DUE:20240120T170000Z',"
                    + "'STATUS:NEEDS-ACTION',"
                    + "'PRIORITY:2',"
                    + "'PERCENT-COMPLETE:40',"
                    + "'END:VTODO',"
                    + "'END:VCALENDAR'], chr(10))";

    /**
     * {@link #SAMPLE_ICS_TEXT_SQL} cast to {@code BLOB}, so executable examples
     * need no external {@code .ics} file — the worker resolves it as raw
     * {@code .ics} bytes rather than a filesystem path.
     */
    static final String SAMPLE_ICS_BLOB = SAMPLE_ICS_TEXT_SQL + "::BLOB";

    /**
     * Render a {@code vgi.result_columns_schema} tag (VGI307/VGI321): a JSON array
     * of {@code {name, type, description}} objects describing a table function's
     * static result columns. {@code cols} is a flat sequence of
     * {@code name, type, description} triples.
     */
    static String resultColumnsSchema(String... cols) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i + 2 < cols.length; i += 3) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"").append(jsonStr(cols[i]))
                    .append("\",\"type\":\"").append(jsonStr(cols[i + 1]))
                    .append("\",\"description\":\"").append(jsonStr(cols[i + 2]))
                    .append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String jsonStr(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
