package com.shopsphere.server.security;

import com.shopsphere.common.protocol.Role;
import java.util.EnumSet;
import java.util.Set;

/** What a role is allowed to do. Checked on the server for every request. */
public enum Permission {
    SHOP,               // cart, checkout, own orders, wishlist, reviews (customers only)
    VIEW_DASHBOARD, MANAGE_CUSTOMERS, MANAGE_PRODUCTS, MANAGE_CATEGORIES, MANAGE_ORDERS,
    MANAGE_INVENTORY, VIEW_PAYMENTS, VIEW_REPORTS, VIEW_AUDIT,
    MANAGE_ADMINS, MANAGE_SETTINGS, VIEW_SECURITY_LOG;   // SUPER_ADMIN only

    private static final Set<Permission> ADMIN_SET = EnumSet.of(VIEW_DASHBOARD, MANAGE_CUSTOMERS, MANAGE_PRODUCTS,
            MANAGE_CATEGORIES, MANAGE_ORDERS, MANAGE_INVENTORY, VIEW_PAYMENTS, VIEW_REPORTS, VIEW_AUDIT);

    public static Set<Permission> of(Role role) {
        switch (role) {
            case CUSTOMER:    return EnumSet.of(SHOP);
            case ADMIN:       return EnumSet.copyOf(ADMIN_SET);
            case SUPER_ADMIN:
                Set<Permission> all = EnumSet.copyOf(ADMIN_SET);
                all.add(MANAGE_ADMINS);
                all.add(MANAGE_SETTINGS);
                all.add(VIEW_SECURITY_LOG);
                return all;
            default:          return EnumSet.noneOf(Permission.class);
        }
    }

    public static boolean has(Role role, Permission permission) {
        return of(role).contains(permission);
    }
}
