package farm.query.vgi.ical;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the committed SQL E2E fixtures under {@code test/sql/data/} using the
 * same in-test {@link Fixtures} builder the JUnit tests use, so the fixtures are
 * reproducible from source rather than opaque committed files.
 *
 * <p>Usage: {@code SqlFixtureGenerator <output-dir>} (defaults to test/sql/data).
 * Run via the Gradle {@code generateSqlFixtures} task (the Makefile {@code test-sql}
 * target invokes it before haybarn-unittest).
 */
public final class SqlFixtureGenerator {

    private SqlFixtureGenerator() {}

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "test/sql/data");
        Files.createDirectories(dir);

        // team.ics — rich feed: 1 VEVENT (attendees, rrule), 1 VTODO, X-WR-CALNAME.
        Files.writeString(dir.resolve("team.ics"), Fixtures.richCalendar(), StandardCharsets.UTF_8);

        // holiday.ics — single all-day (DATE-valued) VEVENT.
        Files.writeString(dir.resolve("holiday.ics"), Fixtures.allDayCalendar(), StandardCharsets.UTF_8);

        // zoned.ics — TZID event + VTIMEZONE for UTC-normalisation E2E.
        Files.writeString(dir.resolve("zoned.ics"), Fixtures.zonedCalendar(), StandardCharsets.UTF_8);

        // garbage.ics — not iCalendar at all -> is_valid_ical false / no rows.
        Files.write(dir.resolve("garbage.ics"), Fixtures.garbageBytes());

        System.out.println("Wrote SQL fixtures to " + dir.toAbsolutePath());
    }
}
