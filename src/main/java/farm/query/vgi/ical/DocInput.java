package farm.query.vgi.ical;

import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.util.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Resolves the typed input overloads of every iCal function: a
 * {@code VARCHAR} path (the worker opens the {@code .ics} file) <em>or</em> a
 * {@code BLOB}/{@code BINARY} column whose {@code .ics} bytes travelled over
 * Arrow. Other input types are rejected rather than converted to paths.
 */
public record DocInput(byte[] bytes, Path path) {

    /** Construct from a constant scalar argument value + its declared Arrow type. */
    public static DocInput fromArgument(Object value, ArrowType type) {
        if (value == null) {
            return null;
        }
        if (type != null && !(type instanceof ArrowType.Utf8)
                && !(type instanceof ArrowType.LargeUtf8) && !isBinary(type)) {
            throw invalidType();
        }
        if (value instanceof byte[] b) {
            if (type != null && !isBinary(type)) throw invalidType();
            return new DocInput(b, null);
        }
        if (value instanceof Text t) {
            return fromArgument(t.toString(), type);
        }
        if (value instanceof String s) {
            // Binary/blob types may arrive as a String for some transports —
            // honour the declared Arrow type to disambiguate path vs. bytes.
            if (isBinary(type)) {
                return new DocInput(s.getBytes(StandardCharsets.ISO_8859_1), null);
            }
            return new DocInput(null, Path.of(s));
        }
        throw invalidType();
    }

    private static IllegalArgumentException invalidType() {
        return new IllegalArgumentException("input must be a VARCHAR file path or BLOB calendar bytes");
    }

    public boolean isPath() {
        return path != null;
    }

    private static boolean isBinary(ArrowType type) {
        return type instanceof ArrowType.Binary || type instanceof ArrowType.LargeBinary;
    }
}
