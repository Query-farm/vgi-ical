package farm.query.vgi.ical;

import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeStampVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.impl.UnionListWriter;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.util.List;
import java.util.Map;

/** Shared Arrow schemas + cell writers for the iCal table functions. */
public final class IcalSchemas {

    private IcalSchemas() {}

    /**
     * {@code ical_events} output: one row per VEVENT. The two TIMESTAMP columns
     * are UTC-normalised microsecond instants (see {@link IcalEngine} javadoc).
     */
    public static final Schema EVENTS_SCHEMA = new Schema(List.of(
            commented("uid", Schemas.UTF8, "VEVENT unique identifier (UID property)."),
            commented("summary", Schemas.UTF8, "Event title (SUMMARY)."),
            commented("description", Schemas.UTF8, "Long-form event description (DESCRIPTION)."),
            commented("dtstart", utcTimestamp(), "Event start, UTC-normalised (DTSTART). Midnight UTC for all-day events."),
            commented("dtend", utcTimestamp(), "Event end, UTC-normalised (DTEND), or NULL when absent."),
            commented("all_day", Schemas.BOOL, "True when DTSTART is DATE-valued (an all-day event)."),
            commented("location", Schemas.UTF8, "Free-text location (LOCATION)."),
            commented("status", Schemas.UTF8, "CONFIRMED / TENTATIVE / CANCELLED (STATUS)."),
            commented("organizer", Schemas.UTF8, "ORGANIZER value (typically a mailto: URI)."),
            listOfUtf8("attendees", "ATTENDEE values, one element per attendee (VARCHAR[])."),
            commented("rrule", Schemas.UTF8, "Recurrence rule string (RRULE), or NULL for one-off events."),
            commented("sequence", Schemas.INT32, "Revision sequence number (SEQUENCE)."),
            listOfUtf8("categories", "Category tags (CATEGORIES), split on commas, one element per tag (VARCHAR[])."),
            commented("url", Schemas.UTF8, "Associated URL (URL property), or NULL when absent."),
            commented("created", utcTimestamp(), "Creation time, UTC-normalised (CREATED), or NULL when absent."),
            commented("last_modified", utcTimestamp(), "Last-modified time, UTC-normalised (LAST-MODIFIED), or NULL when absent.")));

    /** {@code ical_todos} output: one row per VTODO. */
    public static final Schema TODOS_SCHEMA = new Schema(List.of(
            commented("uid", Schemas.UTF8, "VTODO unique identifier (UID property)."),
            commented("summary", Schemas.UTF8, "To-do title (SUMMARY)."),
            commented("due", utcTimestamp(), "Due date/time, UTC-normalised (DUE)."),
            commented("status", Schemas.UTF8, "NEEDS-ACTION / IN-PROCESS / COMPLETED / CANCELLED (STATUS)."),
            commented("priority", Schemas.INT32, "Priority 0-9, lower is higher priority (PRIORITY)."),
            commented("percent_complete", Schemas.INT32, "Completion percentage 0-100 (PERCENT-COMPLETE).")));

    /** A UTC timestamp(micros) ArrowType — DuckDB TIMESTAMP. */
    static ArrowType utcTimestamp() {
        return Schemas.timestampMicros("UTC");
    }

    static Field commented(String name, ArrowType type, String comment) {
        return new Field(name, new FieldType(true, type, null, Map.of("comment", comment)), null);
    }

    /** A nullable LIST(VARCHAR) field — DuckDB {@code VARCHAR[]}. */
    static Field listOfUtf8(String name, String comment) {
        Field item = new Field(ListVector.DATA_VECTOR_NAME,
                new FieldType(true, Schemas.UTF8, null), null);
        return new Field(name,
                new FieldType(true, new ArrowType.List(), null, Map.of("comment", comment)),
                List.of(item));
    }

    // ---- cell writers ------------------------------------------------------

    static void setUtf8(VectorSchemaRoot root, String col, int row, String value) {
        VarCharVector v = (VarCharVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, new Text(value));
        }
    }

    static void setInt(VectorSchemaRoot root, String col, int row, Integer value) {
        IntVector v = (IntVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, value);
        }
    }

    static void setBool(VectorSchemaRoot root, String col, int row, boolean value) {
        BitVector v = (BitVector) root.getVector(col);
        v.setSafe(row, value ? 1 : 0);
    }

    /** Set a TIMESTAMP(micros, UTC) cell from epoch-micros, or NULL. */
    static void setTimestamp(VectorSchemaRoot root, String col, int row, Long micros) {
        TimeStampVector v = (TimeStampVector) root.getVector(col);
        if (micros == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, micros);
        }
    }

    /** Write a LIST(VARCHAR) cell at {@code row}. */
    static void writeStringList(ListVector listVector, int row, List<String> values) {
        UnionListWriter writer = listVector.getWriter();
        writer.setPosition(row);
        writer.startList();
        if (values != null) {
            for (String s : values) {
                if (s != null) {
                    writer.writeVarChar(s);
                }
            }
        }
        writer.endList();
        listVector.setLastSet(row);
    }
}
