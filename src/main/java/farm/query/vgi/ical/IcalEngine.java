package farm.query.vgi.ical;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.util.CompatibilityHints;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe facade over iCal4j's {@link CalendarBuilder}. Parses {@code .ics}
 * bytes (or a file path) into plain records the table/scalar functions emit.
 *
 * <h2>Time-zone handling</h2>
 * iCalendar {@code DTSTART}/{@code DTEND}/{@code DUE} values come in three flavours
 * (RFC 5545 §3.3.5); each is normalised to a single UTC {@code Instant} so every
 * emitted {@code TIMESTAMP} is comparable:
 * <ul>
 *   <li><b>UTC</b> ({@code 20240115T093000Z}) — taken as-is.</li>
 *   <li><b>Zoned</b> ({@code TZID=America/New_York:20240115T093000}) — iCal4j
 *       resolves the {@code VTIMEZONE}/Olson zone to a {@link ZonedDateTime};
 *       we convert its instant to UTC.</li>
 *   <li><b>Floating</b> (no zone, e.g. {@code 20240115T093000}) — interpreted in
 *       the JVM default zone, then converted to UTC. (Floating times are
 *       wall-clock by spec; absent a viewer zone we use the worker's.)</li>
 * </ul>
 * A <b>DATE</b>-valued {@code DTSTART} (no time component, e.g. {@code 20240115})
 * marks an <em>all-day</em> event: {@code all_day = true} and the timestamp is
 * midnight UTC of that calendar date.
 */
public final class IcalEngine {

    private static final IcalEngine SHARED = new IcalEngine();

    static {
        // Real-world feeds (Google, Outlook, Apple, hand-rolled exports) routinely
        // bend RFC 5545. Relax unfolding/parsing/validation so a single malformed
        // line does not abort the whole calendar; Outlook/Notes quirks are common.
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_VALIDATION, true);
        CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_OUTLOOK_COMPATIBILITY, true);
    }

    public static IcalEngine shared() {
        return SHARED;
    }

    // ---- records emitted to the Arrow layer --------------------------------

    /** One VEVENT. Timestamps are epoch-micros UTC (null when absent/unparsable). */
    public record EventRow(
            String uid,
            String summary,
            String description,
            Long dtStartMicros,
            Long dtEndMicros,
            boolean allDay,
            String location,
            String status,
            String organizer,
            List<String> attendees,
            String rrule,
            Integer sequence,
            List<String> categories,
            String url,
            Long createdMicros,
            Long lastModifiedMicros) {}

    /** One VTODO. */
    public record TodoRow(
            String uid,
            String summary,
            Long dueMicros,
            String status,
            Integer priority,
            Integer percentComplete) {}

    /** The whole parse: components + metadata, or an error message. */
    public record ParseResult(
            List<EventRow> events,
            List<TodoRow> todos,
            String calendarName,
            String error) {

        static ParseResult failure(String error) {
            return new ParseResult(List.of(), List.of(), null, error);
        }

        public boolean ok() { return error == null; }
    }

    // ---- parse entry points ------------------------------------------------

    /** Parse from path-or-bytes; never throws (errors are captured in the result). */
    public ParseResult parse(DocInput input) {
        if (input == null) {
            return ParseResult.failure("null input");
        }
        return parse(input.bytes(), input.path());
    }

    public ParseResult parse(byte[] bytes, Path path) {
        Calendar cal;
        try (InputStream in = open(bytes, path)) {
            // A fresh builder per call: CalendarBuilder is not documented as
            // thread-safe and carries per-parse VTIMEZONE state.
            cal = new CalendarBuilder().build(in);
        } catch (Exception e) {
            return ParseResult.failure(describe(e));
        }
        if (cal == null) {
            return ParseResult.failure("empty calendar");
        }

        List<EventRow> events = new ArrayList<>();
        List<TodoRow> todos = new ArrayList<>();
        for (CalendarComponent c : cal.getComponents()) {
            // Never let one bad component sink the whole feed.
            try {
                if (Component.VEVENT.equals(c.getName())) {
                    events.add(toEvent(c));
                } else if (Component.VTODO.equals(c.getName())) {
                    todos.add(toTodo(c));
                }
            } catch (Exception ignore) {
                // skip the unreadable component, keep the rest
            }
        }
        String name = calendarName(cal);
        return new ParseResult(events, todos, name, null);
    }

    /** {@code true} when the bytes/path parse into a calendar without error. */
    public boolean isValid(DocInput input) {
        return input != null && parse(input).ok();
    }

    /** X-WR-CALNAME if present, else PRODID, else null. */
    public String calendarName(DocInput input) {
        ParseResult r = parse(input);
        return r.ok() ? r.calendarName() : null;
    }

    private static String calendarName(Calendar cal) {
        String wr = firstPropertyValue(cal.getProperties(), "X-WR-CALNAME");
        if (wr != null && !wr.isBlank()) return wr;
        String prodId = firstPropertyValue(cal.getProperties(), Property.PRODID);
        return prodId == null || prodId.isBlank() ? null : prodId;
    }

    // ---- component -> record ----------------------------------------------

    private static EventRow toEvent(Component c) {
        List<Property> props = c.getProperties();
        Property dtStart = firstProperty(props, Property.DTSTART);
        boolean allDay = isDateValued(dtStart);
        List<String> attendees = new ArrayList<>();
        for (Property p : props) {
            if (Property.ATTENDEE.equalsIgnoreCase(p.getName())) {
                String v = p.getValue();
                if (v != null && !v.isBlank()) attendees.add(v);
            }
        }
        return new EventRow(
                firstPropertyValue(props, Property.UID),
                firstPropertyValue(props, Property.SUMMARY),
                firstPropertyValue(props, Property.DESCRIPTION),
                toMicros(dtStart),
                toMicros(firstProperty(props, Property.DTEND)),
                allDay,
                firstPropertyValue(props, Property.LOCATION),
                firstPropertyValue(props, Property.STATUS),
                firstPropertyValue(props, Property.ORGANIZER),
                attendees,
                firstPropertyValue(props, Property.RRULE),
                parseInt(firstPropertyValue(props, Property.SEQUENCE)),
                categoriesOf(props),
                firstPropertyValue(props, Property.URL),
                toMicros(firstProperty(props, Property.CREATED)),
                toMicros(firstProperty(props, Property.LAST_MODIFIED)));
    }

    /**
     * Collect an event's category tags. A CATEGORIES property carries a
     * comma-separated list of tags (RFC 5545 §3.8.1.2), and a component may repeat
     * the property, so we gather every CATEGORIES value, split each on commas, and
     * return the trimmed, non-blank tags in source order.
     */
    private static List<String> categoriesOf(List<Property> props) {
        List<String> out = new ArrayList<>();
        for (Property p : props) {
            if (Property.CATEGORIES.equalsIgnoreCase(p.getName())) {
                String v = p.getValue();
                if (v == null) continue;
                for (String tag : v.split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) out.add(trimmed);
                }
            }
        }
        return out;
    }

    private static TodoRow toTodo(Component c) {
        List<Property> props = c.getProperties();
        return new TodoRow(
                firstPropertyValue(props, Property.UID),
                firstPropertyValue(props, Property.SUMMARY),
                toMicros(firstProperty(props, Property.DUE)),
                firstPropertyValue(props, Property.STATUS),
                parseInt(firstPropertyValue(props, Property.PRIORITY)),
                parseInt(firstPropertyValue(props, Property.PERCENT_COMPLETE)));
    }

    // ---- date/time normalisation ------------------------------------------

    /** A DATE-valued property (no time) means an all-day event in iCalendar. */
    private static boolean isDateValued(Property p) {
        if (!(p instanceof DateProperty<?> dp)) return false;
        try {
            Temporal t = dp.getDate();
            return t instanceof LocalDate;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalise a date/date-time property to epoch microseconds (UTC). Returns
     * {@code null} when the property is missing or its value cannot be read.
     * See the class javadoc for the floating/zoned/UTC/all-day rules.
     */
    static Long toMicros(Property p) {
        if (!(p instanceof DateProperty<?> dp)) return null;
        Temporal t;
        try {
            t = dp.getDate();
        } catch (Exception e) {
            return null;
        }
        if (t == null) return null;
        Instant instant = toInstant(t);
        if (instant == null) return null;
        // micros since epoch; saturate rather than overflow on absurd dates.
        try {
            return Math.multiplyExact(instant.getEpochSecond(), 1_000_000L)
                    + instant.getNano() / 1_000L;
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static Instant toInstant(Temporal t) {
        if (t instanceof Instant i) {
            return i;
        }
        if (t instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (t instanceof ZonedDateTime zdt) {
            return zdt.toInstant();
        }
        if (t instanceof LocalDate ld) {
            // All-day: midnight UTC of that calendar date.
            return ld.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
        if (t instanceof LocalDateTime ldt) {
            // Floating wall-clock time: anchor in the worker's default zone.
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        }
        return null;
    }

    // ---- property helpers --------------------------------------------------

    private static Property firstProperty(List<Property> props, String name) {
        for (Property p : props) {
            if (name.equalsIgnoreCase(p.getName())) return p;
        }
        return null;
    }

    private static String firstPropertyValue(List<Property> props, String name) {
        Property p = firstProperty(props, name);
        if (p == null) return null;
        String v = p.getValue();
        return v == null || v.isEmpty() ? null : v;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- io ----------------------------------------------------------------

    private static InputStream open(byte[] bytes, Path path) throws IOException {
        if (path != null) {
            return Files.newInputStream(path);
        }
        return new ByteArrayInputStream(bytes != null ? bytes : new byte[0]);
    }

    private static String describe(Throwable t) {
        String msg = t.getMessage();
        String type = t.getClass().getSimpleName();
        return msg == null || msg.isBlank() ? type : type + ": " + msg;
    }
}
