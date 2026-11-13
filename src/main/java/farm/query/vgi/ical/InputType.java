package farm.query.vgi.ical;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.List;

/** The two SQL overloads shared by the calendar functions. */
public enum InputType {
    PATH("path", Schemas.UTF8, "Filesystem path to an iCalendar (.ics) file, opened by the worker."),
    BYTES("content", Schemas.BINARY, "Raw iCalendar (.ics) bytes, parsed directly without opening a file.");

    private final String argumentName;
    private final ArrowType arrowType;
    private final String doc;

    InputType(String argumentName, ArrowType arrowType, String doc) {
        this.argumentName = argumentName;
        this.arrowType = arrowType;
        this.doc = doc;
    }

    ArgSpec argumentSpec() {
        return new ArgSpec(argumentName, 0, arrowType,
                doc + " NULL input returns no rows for table functions and NULL for scalars.",
                false, false, "", List.of(), false, false, false);
    }
}
