package com.shopsphere.common.protocol;

/** User roles. Higher ordinal means more authority. */
public enum Role {
    CUSTOMER, ADMIN, SUPER_ADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
