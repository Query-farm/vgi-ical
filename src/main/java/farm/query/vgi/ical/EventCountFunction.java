package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * {@code ical.ical_event_count(path | bytes) -> INT} — the number of VEVENTs in
 * an .ics feed. NULL input yields NULL; an unparsable feed yields 0.
 */
public final class EventCountFunction extends ScalarFn {

    private final IcalEngine engine;

    public EventCountFunction() { this(IcalEngine.shared()); }
    public EventCountFunction(IcalEngine engine) { this.engine = engine; }

    @Override public String name() { return "ical_event_count"; }

    @Override public String description() {
        return "Count the VEVENTs in an .ics feed (0 when the feed is empty or unparsable).";
    }

    @Override public FunctionMetadata metadata() {
        java.util.Map<String, String> tags = Meta.objectTags(
                "Count iCalendar Events",
                "# ical_event_count\n\n"
                        + "Return the **number of VEVENTs** in an iCalendar (`.ics`) feed without "
                        + "materialising the rows. Use it as a cheap probe — e.g. to skip empty "
                        + "feeds or rank feeds by activity.\n\n"
                        + "**Input** (positional, polymorphic): a VARCHAR file path the worker "
                        + "opens, or a BLOB of `.ics` bytes.\n\n"
                        + "**Output**: an INTEGER count. NULL input yields NULL; an empty or "
                        + "unparsable feed yields `0` (never an error).",
                "Counts the VEVENTs in an iCalendar (`.ics`) feed.\n\n"
                        + "Accepts a VARCHAR file path or a BLOB of `.ics` bytes and returns an "
                        + "INTEGER. A NULL argument returns NULL; an empty or unparsable feed "
                        + "returns `0`. Cheaper than `SELECT count(*) FROM ical_events(...)` when "
                        + "you only need the tally.",
                "ical event count, count events, number of events, vevent count, "
                        + "calendar size, ics count",
                "EventCountFunction.java");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT ical.main.ical_event_count(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS event_count;\", \"description\": \"Count the VEVENTs in an "
                        + "iCalendar feed.\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"Count the VEVENTs in an inline iCalendar feed.\", "
                        + "\"sql\": \"SELECT ical.main.ical_event_count(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS event_count\"}]");
        return FunctionMetadata.describe(description())
                .withCategories("calendar", "icalendar", "ical4j")
                .withTags(tags)
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT ical.main.ical_event_count(" + Meta.SAMPLE_ICS_BLOB
                                + ") AS event_count;",
                        "Count the VEVENTs in an inline iCalendar feed.",
                        null)));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.INT32;
    }

    public void compute(@Vector(value = "input", any = true) FieldVector in, IntVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            DocInput input = ScalarInput.at(in, i);
            if (input == null) { out.setNull(i); continue; }
            int count;
            try {
                IcalEngine.ParseResult r = engine.parse(input);
                count = r.ok() ? r.events().size() : 0;
            } catch (Throwable t) {
                count = 0;
            }
            out.setSafe(i, count);
        }
    }
}
