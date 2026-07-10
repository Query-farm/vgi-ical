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
import org.apache.arrow.vector.types.pojo.ArrowType;

import java.util.List;

/**
 * {@code ical.ical_todos(path | bytes)} — one row per VTODO in an iCalendar feed
 * (uid, summary, UTC-normalised due, status, priority, percent_complete).
 *
 * <p>A NULL argument or an unparsable feed yields no rows.
 */
public final class IcalTodosFunction implements TableFunction {

    private final IcalEngine engine;

    public IcalTodosFunction() { this(IcalEngine.shared()); }
    public IcalTodosFunction(IcalEngine engine) { this.engine = engine; }

    @Override public String name() { return "ical_todos"; }

    @Override public FunctionMetadata metadata() {
        java.util.Map<String, String> tags = Meta.objectTags(
                "Parse iCalendar Todos",
                "# ical_todos\n\n"
                        + "Parse an iCalendar (`.ics`) feed into **one row per VTODO** (task). Use "
                        + "it to pull outstanding to-dos out of a shared calendar for task "
                        + "tracking or reporting.\n\n"
                        + "**Input** (positional, polymorphic): a VARCHAR file path the worker "
                        + "opens, or a BLOB of `.ics` bytes.\n\n"
                        + "**Output**: uid, summary, UTC-normalised `due` (`TIMESTAMP WITH TIME "
                        + "ZONE`), status (`NEEDS-ACTION` / `IN-PROCESS` / `COMPLETED` / "
                        + "`CANCELLED`), priority (0-9), and percent complete (0-100).\n\n"
                        + "**Edge cases**: a NULL argument or an unparsable feed yields *no rows* "
                        + "(never an error). A feed with only events (no VTODOs) yields no rows.",
                "Parses an iCalendar (`.ics`) feed into one row per VTODO (task), backed by "
                        + "iCal4j (RFC 5545).\n\n"
                        + "Accepts a VARCHAR file path or a BLOB of `.ics` bytes. The `due` column "
                        + "is normalised to UTC and surfaced as `TIMESTAMP WITH TIME ZONE`. Filter "
                        + "on `status` to separate open from completed tasks. Bad or NULL input "
                        + "yields no rows rather than an error.",
                "ical todos, vtodo, tasks, to-do, calendar tasks, due date, priority, "
                        + "percent complete, task tracking, ics tasks",
                "IcalTodosFunction.java");
        tags.put("vgi.category", "To-dos");
        // VGI307/VGI321: structured result schema (JSON array of {name,type,description}).
        // The types mirror the Arrow TODOS_SCHEMA so VGI910 (DESCRIBE) agrees.
        tags.put("vgi.result_columns_schema", Meta.resultColumnsSchema(
                "uid", "VARCHAR", "VTODO unique identifier (UID).",
                "summary", "VARCHAR", "To-do title (SUMMARY).",
                "due", "TIMESTAMP WITH TIME ZONE", "Due date/time, UTC-normalised (DUE), or NULL.",
                "status", "VARCHAR",
                "To-do status: NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED (STATUS).",
                "priority", "INTEGER", "Priority 0-9, where lower is higher priority (PRIORITY).",
                "percent_complete", "INTEGER",
                "Completion percentage 0-100 (PERCENT-COMPLETE)."));
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT summary, status, percent_complete FROM "
                        + "ical.main.ical_todos(" + Meta.SAMPLE_ICS_BLOB + ") "
                        + "WHERE status <> 'COMPLETED' ORDER BY due;\", \"description\": "
                        + "\"List the outstanding to-dos from an iCalendar feed by due date.\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"List the open to-dos from an inline iCalendar feed.\", "
                        + "\"sql\": \"SELECT summary, status, percent_complete FROM "
                        + "ical.main.ical_todos(" + Meta.SAMPLE_ICS_BLOB
                        + ") WHERE status <> 'COMPLETED' ORDER BY due\"}]");
        return FunctionMetadata.describe(
                        "Parse an iCalendar (.ics) feed into one row per VTODO: uid, summary, "
                                + "UTC-normalised due date, status, priority, and percent complete (iCal4j).")
                .withCategories("calendar", "icalendar", "ical4j")
                .withTags(tags)
                .withExamples(java.util.List.of(new FunctionExample(
                        "SELECT summary, status, percent_complete FROM ical.main.ical_todos("
                                + Meta.SAMPLE_ICS_BLOB + ") WHERE status <> 'COMPLETED' "
                                + "ORDER BY due;",
                        "List the open to-dos from an inline iCalendar feed.",
                        null)));
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Polymorphic input carrying a per-argument doc (VGI312); see
        // ArgSpec.any(...) for the field layout.
        return List.of(new ArgSpec(
                "input", 0, new ArrowType.Null(), Meta.INPUT_ARG_DOC,
                false, false, "", List.of(), false, true, false));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(IcalSchemas.TODOS_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) { return 8L; }

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

            if (!hasInput) { out.finish(); return; }

            DocInput input = new DocInput(bytes, path == null ? null : java.nio.file.Path.of(path));
            IcalEngine.ParseResult r = engine().parse(input);
            List<IcalEngine.TodoRow> todos = r.todos();
            if (todos.isEmpty()) { out.finish(); return; }

            VectorSchemaRoot root = VectorSchemaRoot.create(IcalSchemas.TODOS_SCHEMA, Allocators.root());
            root.allocateNew();
            for (int i = 0; i < todos.size(); i++) {
                IcalEngine.TodoRow t = todos.get(i);
                IcalSchemas.setUtf8(root, "uid", i, t.uid());
                IcalSchemas.setUtf8(root, "summary", i, t.summary());
                IcalSchemas.setTimestamp(root, "due", i, t.dueMicros());
                IcalSchemas.setUtf8(root, "status", i, t.status());
                IcalSchemas.setInt(root, "priority", i, t.priority());
                IcalSchemas.setInt(root, "percent_complete", i, t.percentComplete());
            }
            root.setRowCount(todos.size());
            out.emit(root);
            out.finish();
        }
    }
}
