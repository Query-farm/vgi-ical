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
        return FunctionMetadata.describe(description())
                .withCategories("calendar", "icalendar", "ical4j")
                .withTag("vgi.example_queries",
                        "[{\"sql\": \"SELECT ical.main.ical_calendar_name('/cal/team.ics');\", "
                                + "\"description\": \"Read the display name of an iCalendar feed "
                                + "(X-WR-CALNAME, falling back to PRODID).\"}]")
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT ical.main.ical_calendar_name('/cal/team.ics');",
                        "Read the display name of an iCalendar feed (X-WR-CALNAME, "
                                + "falling back to PRODID).",
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
