package com.shopsphere.common.protocol;

import java.util.EnumSet;
import java.util.Set;

/** Order life cycle. The allowed transitions are enforced by the server. */
public enum OrderStatus {
    PENDING_PAYMENT, PAYMENT_FAILED, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

    /** Statuses an administrator may move this order to. */
    public Set<OrderStatus> allowedNext() {
        switch (this) {
            case PAID:       return EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING: return EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED:    return EnumSet.of(DELIVERED);
            default:         return EnumSet.noneOf(OrderStatus.class);
        }
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    /** A customer may cancel only before the order is processed. */
    public boolean customerMayCancel() {
        return this == PAID;
    }
}
