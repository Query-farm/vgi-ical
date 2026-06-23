package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.scalar.Vector;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * {@code ical.is_valid_ical(path | bytes) -> BOOLEAN} — true when the bytes/path
 * parse as a well-formed iCalendar feed (compatibility-relaxed). NULL input
 * yields NULL; malformed bytes yield false (never an error).
 */
public final class IsValidFunction extends ScalarFn {

    private final IcalEngine engine;

    public IsValidFunction() { this(IcalEngine.shared()); }
    public IsValidFunction(IcalEngine engine) { this.engine = engine; }

    @Override public String name() { return "is_valid_ical"; }

    @Override public String description() {
        return "True when the input parses as a well-formed iCalendar (.ics) feed (iCal4j, relaxed).";
    }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(description()).withCategories("calendar", "icalendar", "ical4j");
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    public void compute(@Vector(value = "input", any = true) FieldVector in, BitVector out) {
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            DocInput input = ScalarInput.at(in, i);
            if (input == null) { out.setNull(i); continue; }
            boolean valid;
            try {
                valid = engine.isValid(input);
            } catch (Throwable t) {
                valid = false;
            }
            out.setSafe(i, valid ? 1 : 0);
        }
    }
}
