package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** In-process coverage for ical_todos. */
class IcalTodosTest {

    private static Arguments bytesArg(String ics) {
        return new Arguments(
                java.util.Arrays.asList((Object) ics.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Map.of());
    }

    private static long microsUtc(int y, int mo, int d, int h, int mi) {
        Instant i = OffsetDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant();
        return i.getEpochSecond() * 1_000_000L + i.getNano() / 1_000L;
    }

    @Test void parsesTodoRowValues() {
        var result = TestSupport.invoke(new IcalTodosFunction(), bytesArg(Fixtures.richCalendar()));
        List<Map<String, Object>> rows = result.rows();
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);
        assertEquals("todo-1@query.farm", r.get("uid"));
        assertEquals("Write release notes", r.get("summary"));
        assertEquals("NEEDS-ACTION", r.get("status"));
        assertEquals(2, r.get("priority"));
        assertEquals(40, r.get("percent_complete"));
        assertEquals(microsUtc(2024, 1, 20, 17, 0), ((Number) r.get("due")).longValue());
    }

    @Test void malformedFeedYieldsNoTodos() {
        var result = TestSupport.invoke(new IcalTodosFunction(),
                new Arguments(java.util.Arrays.asList((Object) Fixtures.garbageBytes()), Map.of()));
        assertEquals(0, result.totalRows());
    }
}
