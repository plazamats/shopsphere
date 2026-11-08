package com.shopsphere.server.service;

import com.shopsphere.common.protocol.ErrorCode;

/** A problem the client may be told about. The message must be safe to show to a customer. */
public class ShopException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ErrorCode code;

    public ShopException(ErrorCode code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ShopException notFound(String what) {
        return new ShopException(ErrorCode.NOT_FOUND, what + " was not found");
    }

    public static ShopException forbidden(String message) {
        return new ShopException(ErrorCode.FORBIDDEN, message);
    }

    public static ShopException invalid(String message) {
        return new ShopException(ErrorCode.VALIDATION_FAILED, message);
    }
}
