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
 *   <li>{@code vgi.keywords} (VGI126) — comma-separated search terms/synonyms.</li>
 *   <li>{@code vgi.source_url} (VGI128) — link to the implementing source file.</li>
 * </ul>
 */
final class Meta {

    private Meta() {}

    /** Base GitHub blob URL for source files in this repo (pinned to {@code main}). */
    private static final String SOURCE_BASE =
            "https://github.com/Query-farm/vgi-ical/blob/main/src/main/java/farm/query/vgi/ical";

    /** Build the canonical {@code vgi.source_url} for a Java source file. */
    static String sourceUrl(String javaFileName) {
        return SOURCE_BASE + "/" + javaFileName;
    }

    /**
     * Build the five standard per-object discovery/description tags.
     *
     * @param title        human display name (VGI124)
     * @param docLlm       Markdown narrative for an LLM/agent audience (VGI112)
     * @param docMd        Markdown narrative for human docs (VGI113), distinct from {@code docLlm}
     * @param keywords     comma-separated search terms (VGI126)
     * @param javaFileName implementing source file, e.g. {@code "IcalEventsFunction.java"} (VGI128)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String javaFileName) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("vgi.title", title);
        tags.put("vgi.doc_llm", docLlm);
        tags.put("vgi.doc_md", docMd);
        tags.put("vgi.keywords", keywords);
        tags.put("vgi.source_url", sourceUrl(javaFileName));
        return tags;
    }

    /**
     * A self-contained iCalendar feed expressed as a DuckDB SQL {@code BLOB}
     * expression, so executable examples need no external {@code .ics} file. The
     * feed has one VEVENT (with two attendees and an RRULE) and one VTODO; lines
     * are joined with {@code chr(10)} and the whole thing cast to {@code BLOB} so
     * the worker resolves it as raw {@code .ics} bytes rather than a file path.
     */
    static final String SAMPLE_ICS_BLOB =
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
                    + "'END:VCALENDAR'], chr(10))::BLOB";
}
