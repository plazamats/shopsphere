package com.shopsphere.server.database;

/** Wraps a SQLException. It is logged on the server and never shown to a client. */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
