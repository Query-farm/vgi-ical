package farm.query.vgi.ical;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarBinaryVector;
import org.apache.arrow.vector.VarCharVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coverage for the scalars: ical_calendar_name, ical_event_count, is_valid_ical. */
class ScalarFunctionsTest {

    private final RootAllocator alloc = new RootAllocator();

    @AfterEach void tearDown() { alloc.close(); }

    /** A BLOB vector of .ics byte payloads (null entries become Arrow nulls). */
    private VarBinaryVector blobVec(byte[]... values) {
        VarBinaryVector v = new VarBinaryVector("input", alloc);
        v.allocateNew();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) v.setNull(i);
            else v.setSafe(i, values[i]);
        }
        v.setValueCount(values.length);
        return v;
    }

    private static byte[] rich() {
        return Fixtures.richCalendar().getBytes(StandardCharsets.UTF_8);
    }

    @Test void calendarNameUsesWrCalName() {
        try (VarBinaryVector in = blobVec(rich(), null, Fixtures.garbageBytes());
             VarCharVector out = new VarCharVector("out", alloc)) {
            out.allocateNew();
            new CalendarNameFunction().compute(in, out);
            assertEquals("Team Calendar", out.getObject(0).toString());
            assertTrue(out.isNull(1), "null input -> null name");
            assertTrue(out.isNull(2), "garbage -> null name");
        }
    }

    @Test void eventCountCountsVevents() {
        try (VarBinaryVector in = blobVec(rich(), null, Fixtures.garbageBytes());
             IntVector out = new IntVector("out", alloc)) {
            out.allocateNew();
            new EventCountFunction().compute(in, out);
            assertEquals(1, out.get(0));
            assertTrue(out.isNull(1), "null input -> null count");
            assertEquals(0, out.get(2), "garbage -> 0 events");
        }
    }

    @Test void isValidTrueForGoodFalseForGarbage() {
        try (VarBinaryVector in = blobVec(rich(), null, Fixtures.garbageBytes());
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new IsValidFunction().compute(in, out);
            assertTrue(out.get(0) != 0, "rich feed is valid");
            assertTrue(out.isNull(1), "null input -> null");
            assertFalse(out.get(2) != 0, "garbage is invalid");
        }
    }
}
