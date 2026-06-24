package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

/**
 * {@code ical.ical_calendar_name(path | bytes) -> VARCHAR} — the calendar's
 * display name: {@code X-WR-CALNAME} if present, else {@code PRODID}, else NULL
 * (also NULL for unparsable input).
 */
public final class CalendarNameFunction extends ScalarFn {

    private final IcalEngine engine;

    public CalendarNameFunction() { this(IcalEngine.shared()); }
    public CalendarNameFunction(IcalEngine engine) { this.engine = engine; }

    @Override public String name() { return "ical_calendar_name"; }

    @Override public String description() {
        return "The calendar's display name from an .ics feed (X-WR-CALNAME, falling back to PRODID).";
    }

    @Override public FunctionMetadata metadata() {
        java.util.Map<String, String> tags = Meta.objectTags(
                "iCalendar Display Name",
                "# ical_calendar_name\n\n"
                        + "Return the **display name** of an iCalendar (`.ics`) feed: the "
                        + "`X-WR-CALNAME` property if present, otherwise the `PRODID`.\n\n"
                        + "**Input** (positional, polymorphic): a VARCHAR file path the worker "
                        + "opens, or a BLOB of `.ics` bytes.\n\n"
                        + "**Output**: a VARCHAR name, or NULL when neither property is present or "
                        + "the input is NULL / unparsable.\n\n"
                        + "Use it to label a feed in a UI or to group rows by source calendar.",
                "Returns the display name of an iCalendar (`.ics`) feed, reading `X-WR-CALNAME` "
                        + "and falling back to `PRODID`.\n\n"
                        + "Accepts a VARCHAR file path or a BLOB of `.ics` bytes. Returns NULL "
                        + "when the calendar has no name property, or when the input is NULL or "
                        + "fails to parse — never an error.",
                "ical calendar name, x-wr-calname, prodid, calendar title, feed name, "
                        + "calendar display name, ics name",
                "CalendarNameFunction.java");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT ical.main.ical_calendar_name(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS calendar_name;\", \"description\": \"Read the display name of an "
                        + "iCalendar feed (X-WR-CALNAME, falling back to PRODID).\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"Read the display name of an inline iCalendar feed.\", "
                        + "\"sql\": \"SELECT ical.main.ical_calendar_name(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS calendar_name\"}]");
        return FunctionMetadata.describe(description())
                .withCategories("calendar", "icalendar", "ical4j")
                .withTags(tags)
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT ical.main.ical_calendar_name(" + Meta.SAMPLE_ICS_BLOB
                                + ") AS calendar_name;",
                        "Read the display name of an inline iCalendar feed.",
                        null)));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    public void compute(@Vector(value = "input", any = true) FieldVector in, VarCharVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            DocInput input = ScalarInput.at(in, i);
            if (input == null) { out.setNull(i); continue; }
            String name;
            try {
                name = engine.calendarName(input);
            } catch (Throwable t) {
                name = null;
            }
            if (name == null) out.setNull(i);
            else out.setSafe(i, new Text(name));
        }
    }
}
