package com.shopsphere.common.protocol;

/**
 * Every request the client may send. The payload type for each command is documented in
 * docs/PROTOCOL.md. Who may call a command is decided on the server (CommandPolicy).
 */
public enum Command {
    // authentication and profile
    REGISTER, LOGIN, LOGOUT, GET_PROFILE, UPDATE_PROFILE, CHANGE_PASSWORD,
    LIST_ADDRESSES, ADD_ADDRESS, DELETE_ADDRESS,
    // catalogue
    LIST_CATEGORIES, LIST_PRODUCTS, GET_PRODUCT, LIST_REVIEWS, ADD_REVIEW,
    // cart and wishlist
    GET_CART, ADD_TO_CART, UPDATE_CART_ITEM, REMOVE_CART_ITEM,
    GET_WISHLIST, ADD_WISHLIST, REMOVE_WISHLIST,
    // orders
    CHECKOUT, LIST_MY_ORDERS, GET_ORDER, CANCEL_ORDER, GET_RECEIPT,
    // administration
    GET_DASHBOARD, LIST_USERS, SET_USER_STATUS, SAVE_PRODUCT, ARCHIVE_PRODUCT,
    SAVE_CATEGORY, DELETE_CATEGORY, LIST_ALL_ORDERS, UPDATE_ORDER_STATUS,
    LIST_INVENTORY, ADJUST_STOCK, LIST_STOCK_MOVEMENTS, LIST_PAYMENTS, GET_REPORT, LIST_AUDIT_LOGS,
    // super administrator
    LIST_ADMINS, CREATE_ADMIN, SET_USER_ROLE, LIST_SETTINGS, UPDATE_SETTING
}
