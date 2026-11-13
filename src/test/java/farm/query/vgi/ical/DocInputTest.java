package farm.query.vgi.ical;

import farm.query.vgi.types.Schemas;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocInputTest {

    @Test void rejectsUnsupportedValuesInsteadOfStringifyingPaths() {
        for (Object value : List.of(123, true, List.of("team.ics"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> DocInput.fromArgument(value, null));
        }
    }

    @Test void rejectsUnsupportedDeclaredTypesAndMismatchedBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> DocInput.fromArgument("123", Schemas.INT32));
        assertThrows(IllegalArgumentException.class,
                () -> DocInput.fromArgument(new byte[]{1, 2}, Schemas.UTF8));
    }

    @Test void preservesBinaryStringsUsedBySomeTransports() {
        assertArrayEquals(new byte[]{0, (byte) 0xff},
                DocInput.fromArgument("\u0000\u00ff", Schemas.BINARY).bytes());
    }
}
