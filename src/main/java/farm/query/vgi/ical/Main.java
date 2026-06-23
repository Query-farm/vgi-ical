package farm.query.vgi.ical;

import farm.query.vgi.Worker;

/**
 * VGI worker entry point for iCalendar (.ics) parsing via iCal4j.
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * INSTALL vgi FROM community; LOAD vgi;
 * ATTACH 'ical' (TYPE vgi, LOCATION 'java -jar vgi-ical-all.jar');
 * SELECT summary, dtstart FROM ical.ical_events('/cal/team.ics') ORDER BY dtstart;
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_ICAL_GIT_COMMIT") != null
                    ? System.getenv("VGI_ICAL_GIT_COMMIT") : "unknown";

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("ical")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("iCalendar (.ics) events, todos, and metadata parsing (iCal4j)")
                .registerTable(new IcalEventsFunction())
                .registerTable(new IcalTodosFunction())
                .registerScalar(new CalendarNameFunction())
                .registerScalar(new EventCountFunction())
                .registerScalar(new IsValidFunction());
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        buildWorker().runFromArgs(args);
    }
}
