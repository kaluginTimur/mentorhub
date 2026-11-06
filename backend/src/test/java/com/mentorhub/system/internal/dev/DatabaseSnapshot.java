package com.mentorhub.system.internal.dev;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;

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

    private DatabaseSnapshot() {}

    /** Without time columns: the sample center data is relative to the start. */
    static Map<String, List<String>> withoutTimes(JdbcClient jdbc) {
        return take(jdbc, false);
    }

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
            String columns = jdbc
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
                    .map(column -> '"' + column[0] + '"')
                    .collect(Collectors.joining(", "));
            List<String> rows = jdbc
                    .sql("select row_to_json(t)::text from (select " + columns + " from \"" + table + "\") t")
                    .query(String.class)
                    .list()
                    .stream()
                    .sorted()
                    .toList();
            snapshot.put(table, rows);
        }
        return snapshot;
    }
}
