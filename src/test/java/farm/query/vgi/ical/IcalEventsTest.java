package farm.query.vgi.ical;

import farm.query.vgi.function.Arguments;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In-process coverage for ical_events over a known feed (text and bytes input). */
class IcalEventsTest {

    /** Wrap an .ics string as a single VARCHAR-bytes (a "path-like" string is wrong here,
     *  so we pass the raw bytes via the Arguments positional as a byte[]). */
    private static Arguments bytesArg(byte[] ics) {
        // The any-typed positional accepts a byte[] -> DocInput(bytes).
        return new Arguments(java.util.Arrays.asList((Object) ics), Map.of());
    }

    private static long microsUtc(int y, int mo, int d, int h, int mi) {
        Instant i = OffsetDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant();
        return i.getEpochSecond() * 1_000_000L + i.getNano() / 1_000L;
    }

    @Test void parsesRichEventRowValues() {
        var result = TestSupport.invoke(new IcalEventsFunction(),
                bytesArg(Fixtures.richCalendar().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<Map<String, Object>> rows = result.rows();
        assertEquals(1, rows.size(), "one VEVENT expected");
        Map<String, Object> r = rows.get(0);

        assertEquals("event-1@query.farm", r.get("uid"));
        assertEquals("Sprint Planning", r.get("summary"));
        assertEquals("Plan the next two-week sprint.", r.get("description"));
        assertEquals("Conference Room A", r.get("location"));
        assertEquals("CONFIRMED", r.get("status"));
        assertEquals("mailto:alice@query.farm", r.get("organizer"));
        assertEquals("FREQ=WEEKLY;BYDAY=MO", r.get("rrule"));
        assertEquals(3, r.get("sequence"));
        assertEquals(false, r.get("all_day"));

        // dtstart/dtend are UTC micros.
        assertEquals(microsUtc(2024, 1, 15, 9, 30), ((Number) r.get("dtstart")).longValue());
        assertEquals(microsUtc(2024, 1, 15, 10, 30), ((Number) r.get("dtend")).longValue());

        // attendees array preserved in order, both entries present.
        Object att = r.get("attendees");
        assertTrue(att instanceof List<?>, "attendees should be a list");
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) att;
        assertEquals(List.of("mailto:bob@query.farm", "mailto:carol@query.farm"), attendees);

        // categories: CATEGORIES value split on commas, in source order.
        Object cats = r.get("categories");
        assertTrue(cats instanceof List<?>, "categories should be a list");
        @SuppressWarnings("unchecked")
        List<String> categories = (List<String>) cats;
        assertEquals(List.of("Engineering", "Planning"), categories);

        // url + audit timestamps (CREATED / LAST-MODIFIED), UTC micros.
        assertEquals("https://cal.query.farm/events/event-1", r.get("url"));
        assertEquals(microsUtc(2024, 1, 1, 8, 0), ((Number) r.get("created")).longValue());
        assertEquals(microsUtc(2024, 1, 10, 12, 0), ((Number) r.get("last_modified")).longValue());
    }

    @Test void worksOverByteInput() {
        var result = TestSupport.invoke(new IcalEventsFunction(), bytesArg(Fixtures.richBytes()));
        assertEquals(1, result.totalRows());
        assertEquals("Sprint Planning", result.rows().get(0).get("summary"));
    }

    @Test void allDayEventFlaggedTrue() {
        var result = TestSupport.invoke(new IcalEventsFunction(),
                bytesArg(Fixtures.allDayCalendar().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<Map<String, Object>> rows = result.rows();
        assertEquals(1, rows.size());
        Map<String, Object> r = rows.get(0);
        assertEquals(true, r.get("all_day"));
        // midnight UTC of 2024-07-04.
        assertEquals(microsUtc(2024, 7, 4, 0, 0), ((Number) r.get("dtstart")).longValue());
    }

    @Test void zonedEventNormalisedToUtc() {
        var result = TestSupport.invoke(new IcalEventsFunction(),
                bytesArg(Fixtures.zonedCalendar().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        List<Map<String, Object>> rows = result.rows();
        assertEquals(1, rows.size());
        // 09:00 America/New_York in January (EST, -05:00) == 14:00 UTC.
        assertEquals(microsUtc(2024, 1, 15, 14, 0), ((Number) rows.get(0).get("dtstart")).longValue());
        assertEquals(false, rows.get(0).get("all_day"));
    }

    @Test void malformedFeedYieldsNoRowsNoCrash() {
        var result = TestSupport.invoke(new IcalEventsFunction(), bytesArg(Fixtures.garbageBytes()));
        assertEquals(0, result.totalRows());
    }

    @Test void nullInputYieldsNoRows() {
        var result = TestSupport.invoke(new IcalEventsFunction(),
                new Arguments(java.util.Arrays.asList((Object) null), Map.of()));
        assertEquals(0, result.totalRows());
    }
}
