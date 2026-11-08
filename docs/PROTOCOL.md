# Protocol

Transport: TCP, default port 5555. Each side creates its `ObjectOutputStream` first, flushes, then creates a
filtered `ObjectInputStream`. The server answers every request with exactly one response, in order.

```
Request(requestId, command, token, payload)
Response(requestId, ok, errorCode, message, data)
```

The input filter only allows `com.shopsphere.common.**`, `java.lang`, `java.util`, `java.math` and `java.time`
classes, with depth and array limits. Anything else closes the connection.

`token` is null for the public commands. A token is valid only on the connection that logged in.

## Commands and access

| Group | Commands | Who |
|---|---|---|
| Public | REGISTER, LOGIN, LIST_CATEGORIES, LIST_PRODUCTS, GET_PRODUCT, LIST_REVIEWS | anyone |
| Any logged in user | LOGOUT, GET_PROFILE, UPDATE_PROFILE, CHANGE_PASSWORD, address commands, GET_ORDER, GET_RECEIPT | all roles (orders: owner or admin) |
| Shopping | ADD_REVIEW, cart commands, wishlist commands, CHECKOUT, LIST_MY_ORDERS, CANCEL_ORDER | CUSTOMER only |
| Administration | GET_DASHBOARD, LIST_USERS, SET_USER_STATUS, SAVE_PRODUCT, ARCHIVE_PRODUCT, SAVE_CATEGORY, DELETE_CATEGORY, LIST_ALL_ORDERS, UPDATE_ORDER_STATUS, LIST_INVENTORY, ADJUST_STOCK, LIST_STOCK_MOVEMENTS, LIST_PAYMENTS, GET_REPORT, LIST_AUDIT_LOGS | ADMIN, SUPER_ADMIN |
| Super admin | LIST_ADMINS, CREATE_ADMIN, SET_USER_ROLE, LIST_SETTINGS, UPDATE_SETTING | SUPER_ADMIN |

## Error codes

BAD_REQUEST, VALIDATION_FAILED, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, ACCOUNT_SUSPENDED,
ACCOUNT_LOCKED, INVALID_CREDENTIALS, OUT_OF_STOCK, PAYMENT_FAILED, SERVER_BUSY, INTERNAL_ERROR.
Messages are written for users and never contain SQL, stack traces or class names.

## Checkout

`CHECKOUT(addressId, cardNumber, idempotencyKey)` returns `CheckoutResult(order, paymentSucceeded, message)`.
A declined payment is a normal result (`paymentSucceeded=false`, order PAYMENT_FAILED, stock restored, cart kept),
not an error. Use a new key for each new attempt.
