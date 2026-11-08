package com.shopsphere.common.protocol;

import java.io.Serializable;

/** One response from the server. When ok is false, code and message explain why. */
public record Response(String requestId, boolean ok, ErrorCode code, String message, Serializable data)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public static Response success(String requestId, Serializable data) {
        return new Response(requestId, true, null, null, data);
    }

    public static Response failure(String requestId, ErrorCode code, String message) {
        return new Response(requestId, false, code, message, null);
    }
}
