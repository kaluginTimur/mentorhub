package com.mentorhub.system.internal.dev;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * MentorHub's data as text: every table of the {@code public} schema except the Flyway history and the event
 * publication registry, each as its sorted rows.
 */
final class DatabaseSnapshot {

    private static final Set<String> TIME_TYPES = Set.of(
            "timestamp with time zone",
            "timestamp without time zone",
            "date",
            "time with time zone",
            "time without time zone");

    private static final Set<String> JSON_TYPES = Set.of("json", "jsonb");

    // Stands for a time inside a JSON value: a key still holds a time, whatever the time is.
    private static final StringNode TIME = StringNode.valueOf("<time>");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private DatabaseSnapshot() {}

    /**
     * Without times, since the sample center data is relative to the start: without time columns, and with every time
     * inside a JSON column, such as {@code audit_log.details}, replaced by a placeholder. Everything else inside the
     * JSON is kept as it is. Compares the data of two starts in {@code dev} (DEV-2.1).
     */
    static Map<String, List<String>> withoutTimes(JdbcClient jdbc) {
        return take(jdbc, false);
    }

    /** With every time: a start without {@code dev} must leave the data exactly as it was (DEV-2.2). */
    static Map<String, List<String>> complete(JdbcClient jdbc) {
        return take(jdbc, true);
    }

    private static Map<String, List<String>> take(JdbcClient jdbc, boolean withTimes) {
        List<String> tables = jdbc.sql("""
                        select table_name from information_schema.tables
                        where table_schema = 'public' and table_type = 'BASE TABLE'
                          and table_name <> 'flyway_schema_history' and table_name not like 'event_publication%'
                        order by table_name
                        """).query(String.class).list();
        Map<String, List<String>> snapshot = new TreeMap<>();
        for (String table : tables) {
            List<String[]> columns = jdbc
                    .sql("""
                            select column_name, data_type from information_schema.columns
                            where table_schema = 'public' and table_name = :table
                            order by ordinal_position
                            """)
                    .param("table", table)
                    .query((row, i) -> new String[] {row.getString(1), row.getString(2)})
                    .list()
                    .stream()
                    .filter(column -> withTimes || !TIME_TYPES.contains(column[1]))
                    .toList();
            List<String> jsonColumns = withTimes
                    ? List.of()
                    : columns.stream()
                            .filter(column -> JSON_TYPES.contains(column[1]))
                            .map(column -> column[0])
                            .toList();
            String selected =
                    columns.stream().map(column -> '"' + column[0] + '"').collect(Collectors.joining(", "));
            List<String> rows = jdbc
                    .sql("select row_to_json(t)::text from (select " + selected + " from \"" + table + "\") t")
                    .query(String.class)
                    .list()
                    .stream()
                    .map(row -> jsonColumns.isEmpty() ? row : withoutTimesInside(row, jsonColumns))
                    .sorted()
                    .toList();
            snapshot.put(table, rows);
        }
        return snapshot;
    }

    private static String withoutTimesInside(String row, List<String> jsonColumns) {
        ObjectNode values = (ObjectNode) JSON.readTree(row);
        for (String column : jsonColumns) {
            values.set(column, withoutTimes(values.get(column)));
        }
        return JSON.writeValueAsString(values);
    }

    private static JsonNode withoutTimes(JsonNode value) {
        if (value instanceof ObjectNode object) {
            for (String name : List.copyOf(object.propertyNames())) {
                object.set(name, withoutTimes(object.get(name)));
            }
        } else if (value instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                array.set(i, withoutTimes(array.get(i)));
            }
        } else if (value != null && value.isString() && isTime(value.stringValue())) {
            return TIME;
        }
        return value;
    }

    // An ISO 8601 date-time, with or without an offset, or a date, as times and dates are written in JSON.
    private static boolean isTime(String text) {
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_DATE_TIME, DateTimeFormatter.ISO_DATE)) {
            try {
                format.parse(text);
                return true;
            } catch (DateTimeParseException notThisFormat) {
                // try the next format
            }
        }
        return false;
    }
}
