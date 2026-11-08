package com.shopsphere.client.network;

import com.shopsphere.common.protocol.ErrorCode;


public class ApiException extends Exception {

    private static final long serialVersionUID = 1L;
    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
