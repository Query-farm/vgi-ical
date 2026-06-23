package farm.query.vgi.ical;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
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
        return FunctionMetadata.describe(
                        "Parse an iCalendar (.ics) feed into one row per VTODO: uid, summary, "
                                + "UTC-normalised due date, status, priority, and percent complete (iCal4j).")
                .withCategories("calendar", "icalendar", "ical4j");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of(ArgSpec.any("input", 0, List.of()));
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
