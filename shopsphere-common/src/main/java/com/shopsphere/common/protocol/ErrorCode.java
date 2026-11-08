package com.shopsphere.common.protocol;

/** Safe error codes. The message that goes with a code never contains internal details. */
public enum ErrorCode {
    BAD_REQUEST, VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT,
    ACCOUNT_SUSPENDED, ACCOUNT_LOCKED, INVALID_CREDENTIALS, OUT_OF_STOCK, PAYMENT_FAILED,
    SERVER_BUSY, INTERNAL_ERROR
}
