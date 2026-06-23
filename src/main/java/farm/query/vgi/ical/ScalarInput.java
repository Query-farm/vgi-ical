package farm.query.vgi.ical;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;

import java.nio.file.Path;

/** Resolve a path-or-bytes {@link DocInput} from one cell of an any-typed scalar vector. */
final class ScalarInput {

    private ScalarInput() {}

    /** Null when the cell is null; a path for VARCHAR, raw bytes for BLOB. */
    static DocInput at(FieldVector in, int row) {
        if (in.isNull(row)) return null;
        if (in instanceof VarBinaryVector b) {
            return new DocInput(b.get(row), null);
        }
        if (in instanceof VarCharVector s) {
            return new DocInput(null, Path.of(s.getObject(row).toString()));
        }
        return DocInput.fromArgument(in.getObject(row), in.getField().getType());
    }
}
