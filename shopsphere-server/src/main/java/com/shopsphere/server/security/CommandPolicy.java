package com.shopsphere.server.security;

import com.shopsphere.common.protocol.Command;
import com.shopsphere.common.protocol.ErrorCode;
import com.shopsphere.server.service.ShopException;
import java.util.EnumMap;
import java.util.Map;

/** Who may call which command. A command with no rule here is refused (deny by default). */
public final class CommandPolicy {

    /** A rule is PUBLIC, LOGGED_IN (any role) or a specific permission. */
    private record Rule(boolean isPublic, Permission permission) { }

    private static final Rule PUBLIC = new Rule(true, null);
    private static final Rule LOGGED_IN = new Rule(false, null);
    private static final Map<Command, Rule> RULES = new EnumMap<>(Command.class);

    static {
        for (Command c : new Command[]{Command.REGISTER, Command.LOGIN, Command.LIST_CATEGORIES,
                Command.LIST_PRODUCTS, Command.GET_PRODUCT, Command.LIST_REVIEWS}) {
            RULES.put(c, PUBLIC);
        }
        for (Command c : new Command[]{Command.LOGOUT, Command.GET_PROFILE, Command.UPDATE_PROFILE,
                Command.CHANGE_PASSWORD, Command.GET_ORDER, Command.GET_RECEIPT, Command.LIST_ADDRESSES, Command.ADD_ADDRESS, Command.DELETE_ADDRESS}) {
            RULES.put(c, LOGGED_IN);
        }
        for (Command c : new Command[]{Command.ADD_REVIEW, Command.GET_CART, Command.ADD_TO_CART,
                Command.UPDATE_CART_ITEM, Command.REMOVE_CART_ITEM, Command.GET_WISHLIST, Command.ADD_WISHLIST,
                Command.REMOVE_WISHLIST, Command.CHECKOUT, Command.LIST_MY_ORDERS, Command.CANCEL_ORDER}) {
            RULES.put(c, new Rule(false, Permission.SHOP));
        }
        admin(Command.GET_DASHBOARD, Permission.VIEW_DASHBOARD);
        admin(Command.LIST_USERS, Permission.MANAGE_CUSTOMERS);
        admin(Command.SET_USER_STATUS, Permission.MANAGE_CUSTOMERS);
        admin(Command.SAVE_PRODUCT, Permission.MANAGE_PRODUCTS);
        admin(Command.ARCHIVE_PRODUCT, Permission.MANAGE_PRODUCTS);
        admin(Command.SAVE_CATEGORY, Permission.MANAGE_CATEGORIES);
        admin(Command.DELETE_CATEGORY, Permission.MANAGE_CATEGORIES);
        admin(Command.LIST_ALL_ORDERS, Permission.MANAGE_ORDERS);
        admin(Command.UPDATE_ORDER_STATUS, Permission.MANAGE_ORDERS);
        admin(Command.LIST_INVENTORY, Permission.MANAGE_INVENTORY);
        admin(Command.ADJUST_STOCK, Permission.MANAGE_INVENTORY);
        admin(Command.LIST_STOCK_MOVEMENTS, Permission.MANAGE_INVENTORY);
        admin(Command.LIST_PAYMENTS, Permission.VIEW_PAYMENTS);
        admin(Command.GET_REPORT, Permission.VIEW_REPORTS);
        admin(Command.LIST_AUDIT_LOGS, Permission.VIEW_AUDIT);
        admin(Command.LIST_ADMINS, Permission.MANAGE_ADMINS);
        admin(Command.CREATE_ADMIN, Permission.MANAGE_ADMINS);
        admin(Command.SET_USER_ROLE, Permission.MANAGE_ADMINS);
        admin(Command.LIST_SETTINGS, Permission.MANAGE_SETTINGS);
        admin(Command.UPDATE_SETTING, Permission.MANAGE_SETTINGS);
    }

    private CommandPolicy() { }

    private static void admin(Command c, Permission p) {
        RULES.put(c, new Rule(false, p));
    }

    public static boolean isPublic(Command command) {
        Rule r = RULES.get(command);
        return r != null && r.isPublic();
    }

    /** Throws if the session may not run the command. The session is null for a public command. */
    public static void check(Command command, Session session) {
        Rule rule = RULES.get(command);
        if (rule == null) throw new ShopException(ErrorCode.FORBIDDEN, "This request is not allowed");
        if (rule.isPublic()) return;
        if (session == null) throw new ShopException(ErrorCode.UNAUTHENTICATED, "Please log in first");
        if (rule.permission() != null && !Permission.has(session.role(), rule.permission())) {
            throw new ShopException(ErrorCode.FORBIDDEN, "You do not have permission to do this");
        }
    }

    /** Used by tests to make sure every command has a rule. */
    public static boolean hasRule(Command command) {
        return RULES.containsKey(command);
    }
}
