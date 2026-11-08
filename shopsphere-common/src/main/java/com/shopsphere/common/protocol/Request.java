package com.shopsphere.common.protocol;

import java.io.Serializable;

/**
 * One request from the client. The token identifies the session created at login.
 * The payload is a DTO from com.shopsphere.common.dto (or a Long or String), or null.
 */
public record Request(String requestId, Command command, String token, Serializable payload)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The payload is never printed, because it can hold passwords. */
    @Override
    public String toString() {
        return "Request[" + command + ", id=" + requestId + "]";
    }
}
