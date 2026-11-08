package com.shopsphere.common.protocol;

public enum StockReason {
    INITIAL, PURCHASE, CANCELLATION, PAYMENT_FAILED, RESTOCK, ADJUSTMENT, DAMAGE, CORRECTION;

    /** Reasons an administrator may pick for a manual adjustment. */
    public boolean isManual() {
        return this == RESTOCK || this == ADJUSTMENT || this == DAMAGE || this == CORRECTION;
    }
}
