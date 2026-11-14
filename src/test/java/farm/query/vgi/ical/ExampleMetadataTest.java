package farm.query.vgi.ical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks on every published example list: valid JSON, a description on
 * every entry, within the VGI417 8,000-character cap, no duplicate entries, and
 * each composed example within the first five examples of its driving object
 * (the vgi-lint agent simulator shows only those) and on the schema-level list.
 */
class ExampleMetadataTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int EXAMPLE_LIST_CAP = 8_000;
    private static final int AGENT_VISIBLE = 5;

    /** Every object's tags, keyed by a readable name. */
    private static Map<String, Map<String, String>> allObjectTags() {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        out.put("schema main", Main.SCHEMA_TAGS);
        out.put("example_calendars", ExampleCalendars.view().tags());
        for (InputType t : InputType.values()) {
            out.put("ical_events/" + t, new IcalEventsFunction(t).metadata().tags());
            out.put("ical_todos/" + t, new IcalTodosFunction(t).metadata().tags());
            out.put("ical_calendar_name/" + t, new CalendarNameFunction(t).metadata().tags());
            out.put("ical_event_count/" + t, new EventCountFunction(t).metadata().tags());
            out.put("is_valid_ical/" + t, new IsValidFunction(t).metadata().tags());
        }
        return out;
    }

    private static List<String> sqlOf(String tagValue) throws Exception {
        List<String> sql = new ArrayList<>();
        for (JsonNode n : JSON.readTree(tagValue)) {
            sql.add(n.get("sql").asText());
        }
        return sql;
    }

    @Test void exampleListsAreWellFormedAndWithinCap() throws Exception {
        for (var obj : allObjectTags().entrySet()) {
            for (String key : List.of("vgi.example_queries", "vgi.executable_examples")) {
                String value = obj.getValue().get(key);
                if (value == null) {
                    continue;
                }
                String where = obj.getKey() + " " + key;
                assertTrue(value.length() <= EXAMPLE_LIST_CAP,
                        where + " is " + value.length() + " chars");
                JsonNode arr = JSON.readTree(value);
                assertTrue(arr.isArray(), where);
                List<String> seen = new ArrayList<>();
                for (JsonNode n : arr) {
                    assertFalse(n.path("description").asText().isBlank(), where + " description");
                    String sql = n.path("sql").asText();
                    assertFalse(sql.isBlank(), where + " sql");
                    assertFalse(seen.contains(sql), where + " duplicates " + sql);
                    seen.add(sql);
                }
            }
        }
    }

    @Test void composedExamplesReachAgentsAndTheSchemaList() throws Exception {
        Map<String, Map<String, String>> tags = allObjectTags();
        Map<String, List<String>> driving = Map.of(
                ComposedExamples.FEED_AUDIT_SQL, List.of("example_calendars",
                        "is_valid_ical/PATH", "is_valid_ical/BYTES"),
                ComposedExamples.EVENT_CONFLICTS_SQL, List.of("ical_events/PATH",
                        "ical_events/BYTES"),
                ComposedExamples.TODOS_DUE_WEEK_SQL, List.of("ical_events/PATH",
                        "ical_events/BYTES", "ical_todos/PATH", "ical_todos/BYTES"));
        List<String> schema = sqlOf(Main.SCHEMA_TAGS.get("vgi.example_queries"));
        for (var e : driving.entrySet()) {
            String sql = e.getKey() + ";";
            assertTrue(schema.contains(sql), "schema list lacks " + sql);
            for (String obj : e.getValue()) {
                List<String> list = sqlOf(tags.get(obj).get("vgi.example_queries"));
                int idx = list.indexOf(sql);
                assertTrue(idx >= 0 && idx < AGENT_VISIBLE, obj + " lacks (in first 5) " + sql);
            }
        }
    }
}
