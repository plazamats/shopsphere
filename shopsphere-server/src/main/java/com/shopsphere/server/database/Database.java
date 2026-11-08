package com.shopsphere.server.database;

import com.shopsphere.server.config.ServerConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gives the services a Connection. read() is for queries, transaction() groups several statements
 * so they all succeed or all fail. Connections are always closed (try-with-resources).
 */
public class Database {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final int MAX_RETRIES = 3;

    private final ServerConfig config;

    public Database(ServerConfig config) {
        this.config = config;
    }

    private Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
        return c;
    }

    /** Run queries on one connection (auto-commit). */
    public <T> T read(SqlFunction<T> work) {
        try (Connection c = open()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new DataAccessException("Database read failed: " + e.getMessage(), e);
        }
    }

    /**
     * Run work in one transaction. On any exception the transaction is rolled back and the exception
     * is rethrown. A deadlock or serialization failure is retried a few times.
     */
    public <T> T transaction(SqlFunction<T> work) {
        for (int attempt = 1; ; attempt++) {
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    T result = work.apply(c);
                    c.commit();
                    return result;
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(c);
                    throw e;
                }
            } catch (SQLException e) {
                boolean retryable = "40001".equals(e.getSQLState()) || "40P01".equals(e.getSQLState());
                if (retryable && attempt < MAX_RETRIES) {
                    LOG.log(Level.FINE, "Retrying transaction after {0}", e.getSQLState());
                    continue;
                }
                throw new DataAccessException("Database transaction failed: " + e.getMessage(), e);
            }
        }
    }

    /** True if the database answers. */
    public boolean ping() {
        try {
            return read(c -> c.isValid(3));
        } catch (DataAccessException e) {
            return false;
        }
    }

    /** True if the exception (or its cause) is a unique constraint violation, for example a duplicate email. */
    public static boolean isUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException && "23505".equals(((SQLException) t).getSQLState())) return true;
        }
        return false;
    }

    private static void rollbackQuietly(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Rollback failed: {0}", ex.getMessage());
        }
    }
}
