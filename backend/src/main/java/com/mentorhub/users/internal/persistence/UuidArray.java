package com.mentorhub.users.internal.persistence;

import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.SqlTypeValue;

/**
 * Binds ids as one PostgreSQL {@code uuid[]} parameter, for {@code = any(:ids)} and {@code <> all(:ids)}, which also
 * work for no ids. Needed because {@code JdbcClient} expands a collection into a list of parameters and the driver
 * cannot infer {@code uuid[]} from a {@code UUID[]}.
 */
final class UuidArray {

    private UuidArray() {}

    static SqlTypeValue of(Collection<UUID> ids) {
        Object[] values = ids.toArray();
        return (statement, index, sqlType, typeName) ->
                statement.setArray(index, statement.getConnection().createArrayOf("uuid", values));
    }
}
