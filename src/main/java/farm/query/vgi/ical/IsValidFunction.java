package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
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
        java.util.Map<String, String> tags = Meta.objectTags(
                "Validate iCalendar Feed",
                "# is_valid_ical\n\n"
                        + "Test whether the input parses as a **well-formed iCalendar (`.ics`) "
                        + "feed** under iCal4j's compatibility-relaxed rules. Use it to guard a "
                        + "pipeline before calling `ical_events` / `ical_todos`, or to filter "
                        + "dirty data.\n\n"
                        + "**Input** (positional, polymorphic): a VARCHAR file path the worker "
                        + "opens, or a BLOB of `.ics` bytes.\n\n"
                        + "**Output**: a BOOLEAN. NULL input yields NULL; malformed bytes yield "
                        + "`false` (never an error). Validation is relaxed, so minor RFC "
                        + "violations still count as valid.",
                "Returns true when the input parses as a well-formed iCalendar (`.ics`) feed "
                        + "(iCal4j, compatibility-relaxed).\n\n"
                        + "Accepts a VARCHAR file path or a BLOB of `.ics` bytes. A NULL argument "
                        + "returns NULL; malformed input returns `false` rather than raising an "
                        + "error, so it is safe to call across a column of mixed-quality data.",
                "is valid ical, validate ical, well-formed, ics validation, calendar "
                        + "validation, feed check, parseable, verify ics",
                "IsValidFunction.java");
        tags.put("vgi.category", "Calendar metadata");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT ical.main.is_valid_ical(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS is_valid;\", \"description\": \"Test whether the input parses as a "
                        + "well-formed iCalendar (.ics) feed.\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"Validate an inline iCalendar feed.\", "
                        + "\"sql\": \"SELECT ical.main.is_valid_ical(" + Meta.SAMPLE_ICS_BLOB
                        + ") AS is_valid\"}]");
        return FunctionMetadata.describe(description())
                .withCategories("calendar", "icalendar", "ical4j")
                .withTags(tags)
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT ical.main.is_valid_ical(" + Meta.SAMPLE_ICS_BLOB
                                + ") AS is_valid;",
                        "Validate an inline iCalendar feed.",
                        null)));
    }

    @Override protected ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    public void compute(
            @Vector(value = "input", any = true, doc = Meta.INPUT_ARG_DOC) FieldVector in,
            BitVector out) {
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
