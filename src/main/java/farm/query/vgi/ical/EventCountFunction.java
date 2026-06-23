package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
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
        return FunctionMetadata.describe(description()).withCategories("calendar", "icalendar", "ical4j");
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
