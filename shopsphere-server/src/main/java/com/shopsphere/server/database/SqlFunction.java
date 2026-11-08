package com.shopsphere.server.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Work that uses one Connection. */
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
