package farm.query.vgi.ical;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.table.TableBindParams;
import farm.query.vgi.table.TableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.List;

/**
 * {@code ical.ical_events(path | bytes)} — one row per VEVENT in an iCalendar
 * feed, with UTC-normalised {@code dtstart}/{@code dtend}, an {@code all_day}
 * flag, an {@code attendees VARCHAR[]} array, and the recurrence {@code rrule}.
 *
 * <p>A NULL argument or a feed that fails to parse yields <em>no rows</em> (the
 * worker never crashes the query on bad input — see {@link IcalEngine}).
 */
public final class IcalEventsFunction implements TableFunction {

    private final IcalEngine engine;

    public IcalEventsFunction() { this(IcalEngine.shared()); }
    public IcalEventsFunction(IcalEngine engine) { this.engine = engine; }

    @Override public String name() { return "ical_events"; }

    @Override public FunctionMetadata metadata() {
        java.util.Map<String, String> tags = Meta.objectTags(
                "Parse iCalendar Events",
                "# ical_events\n\n"
                        + "Parse an iCalendar (`.ics`) feed into **one row per VEVENT**. Use it to "
                        + "ingest a calendar feed for scheduling analytics, reporting, or joining "
                        + "events against other tables.\n\n"
                        + "**Input** (positional, polymorphic): a VARCHAR file path the worker "
                        + "opens, or a BLOB of `.ics` bytes.\n\n"
                        + "**Output**: uid, summary, description, UTC-normalised `dtstart`/`dtend` "
                        + "(`TIMESTAMP WITH TIME ZONE`), an `all_day` flag, location, status, "
                        + "organizer, an `attendees VARCHAR[]` array, the recurrence `rrule`, and "
                        + "`sequence`.\n\n"
                        + "**Edge cases**: a NULL argument or a feed that fails to parse yields "
                        + "*no rows* (never an error). DATE-valued starts set `all_day = true`; "
                        + "recurrences are surfaced as the raw RRULE string, not expanded.",
                "Parses an iCalendar (`.ics`) feed into one row per VEVENT, backed by iCal4j "
                        + "(RFC 5545).\n\n"
                        + "Accepts a VARCHAR file path or a BLOB of `.ics` bytes. Timestamps are "
                        + "normalised to UTC and surfaced as `TIMESTAMP WITH TIME ZONE`. Bad or "
                        + "NULL input yields no rows rather than an error. Recurrence rules appear "
                        + "as the raw RRULE string in the `rrule` column.",
                "ical events, vevent, calendar events, ics events, parse calendar, dtstart, "
                        + "dtend, rrule, attendees, scheduling, meetings, appointments",
                "IcalEventsFunction.java");
        tags.put("vgi.result_columns_md",
                "| column | type | description |\n"
                        + "|---|---|---|\n"
                        + "| `uid` | VARCHAR | VEVENT unique identifier (UID). |\n"
                        + "| `summary` | VARCHAR | Event title (SUMMARY). |\n"
                        + "| `description` | VARCHAR | Long-form description (DESCRIPTION). |\n"
                        + "| `dtstart` | TIMESTAMP WITH TIME ZONE | Start, UTC-normalised (DTSTART). |\n"
                        + "| `dtend` | TIMESTAMP WITH TIME ZONE | End, UTC-normalised (DTEND), or NULL. |\n"
                        + "| `all_day` | BOOLEAN | True when DTSTART is DATE-valued. |\n"
                        + "| `location` | VARCHAR | Free-text location (LOCATION). |\n"
                        + "| `status` | VARCHAR | CONFIRMED / TENTATIVE / CANCELLED (STATUS). |\n"
                        + "| `organizer` | VARCHAR | ORGANIZER value (usually a mailto: URI). |\n"
                        + "| `attendees` | VARCHAR[] | ATTENDEE values, one per attendee. |\n"
                        + "| `rrule` | VARCHAR | Recurrence rule (RRULE), or NULL. |\n"
                        + "| `sequence` | INTEGER | Revision sequence number (SEQUENCE). |");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT summary, location, all_day FROM ical.main.ical_events("
                        + Meta.SAMPLE_ICS_BLOB + ") ORDER BY dtstart;\", \"description\": "
                        + "\"List every event in an iCalendar feed by start time.\"}]");
        // VGI509: a guaranteed-runnable, self-contained example using an inline
        // .ics BLOB so no external file is needed.
        tags.put("vgi.executable_examples",
                "[{\"description\": \"Parse an inline iCalendar feed into event rows.\", "
                        + "\"sql\": \"SELECT summary, location, all_day FROM "
                        + "ical.main.ical_events(" + Meta.SAMPLE_ICS_BLOB
                        + ") ORDER BY dtstart\"}]");
        return FunctionMetadata.describe(
                        "Parse an iCalendar (.ics) feed into one row per VEVENT: uid, summary, "
                                + "UTC-normalised start/end, all-day flag, location, status, organizer, "
                                + "attendees array, recurrence rule, and sequence (iCal4j).")
                .withCategories("calendar", "icalendar", "ical4j")
                .withTags(tags)
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT summary, location, all_day FROM ical.main.ical_events("
                                + Meta.SAMPLE_ICS_BLOB + ") ORDER BY dtstart;",
                        "Parse an inline iCalendar feed into event rows.",
                        null)));
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic input: an any-typed positional so DuckDB binds both a
        // VARCHAR path (the worker opens the file) and a BLOB of .ics bytes.
        return List.of(ArgSpec.any("input", 0, List.of()));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(IcalSchemas.EVENTS_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 16L; }

    @Override public TableProducerState createProducer(TableInitParams params) {
        Arguments a = params.arguments();
        Object value = a.positionalAt(0);
        ArrowType type = a.positionalTypeAt(0);
        DocInput input = DocInput.fromArgument(value, type);
        return new State(input, engine);
    }

    public static final class State extends TableProducerState {
        public byte[] bytes;
        public String path;
        public boolean hasInput;
        public boolean done;
        public transient IcalEngine engine;

        public State() {}

        State(DocInput input, IcalEngine engine) {
            this.hasInput = input != null;
            this.bytes = input == null ? null : input.bytes();
            this.path = input == null || input.path() == null ? null : input.path().toString();
            this.engine = engine;
        }

        private IcalEngine engine() { return engine != null ? engine : IcalEngine.shared(); }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            if (!hasInput) { out.finish(); return; } // NULL input -> no rows

            DocInput input = new DocInput(bytes, path == null ? null : java.nio.file.Path.of(path));
            IcalEngine.ParseResult r = engine().parse(input);
            List<IcalEngine.EventRow> events = r.events();
            if (events.isEmpty()) { out.finish(); return; }

            VectorSchemaRoot root = VectorSchemaRoot.create(IcalSchemas.EVENTS_SCHEMA, Allocators.root());
            root.allocateNew();
            ListVector attendeesVec = (ListVector) root.getVector("attendees");
            for (int i = 0; i < events.size(); i++) {
                IcalEngine.EventRow e = events.get(i);
                IcalSchemas.setUtf8(root, "uid", i, e.uid());
                IcalSchemas.setUtf8(root, "summary", i, e.summary());
                IcalSchemas.setUtf8(root, "description", i, e.description());
                IcalSchemas.setTimestamp(root, "dtstart", i, e.dtStartMicros());
                IcalSchemas.setTimestamp(root, "dtend", i, e.dtEndMicros());
                IcalSchemas.setBool(root, "all_day", i, e.allDay());
                IcalSchemas.setUtf8(root, "location", i, e.location());
                IcalSchemas.setUtf8(root, "status", i, e.status());
                IcalSchemas.setUtf8(root, "organizer", i, e.organizer());
                IcalSchemas.writeStringList(attendeesVec, i, e.attendees());
                IcalSchemas.setUtf8(root, "rrule", i, e.rrule());
                IcalSchemas.setInt(root, "sequence", i, e.sequence());
            }
            root.setRowCount(events.size());
            out.emit(root);
            out.finish();
        }
    }
}
