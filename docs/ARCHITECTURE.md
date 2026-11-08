# Architecture

## Layers

| Layer | Package | Job |
|---|---|---|
| Client UI | `client.ui` | Swing screens. No business rules and no database code |
| Client API | `client.network` | `ApiClient` turns each command into a typed method, `ServerConnection` owns the socket |
| Protocol | `common.protocol`, `common.dto` | Shared commands, requests, responses, DTO records |
| Network | `server.network` | `ShopServer` accepts clients, `ClientHandler` serves one, `RequestRouter` dispatches |
| Security | `server.security` | `CommandPolicy` (permissions), `SessionManager`, `PasswordHasher` |
| Services | `server.service` | Business rules, transactions, audit |
| DAO | `server.dao` | SQL only, PreparedStatement, one connection passed in by the service |
| Database | `server.database` | `Database.read` and `Database.transaction` (rollback and retry on deadlock) |

## Request flow

1. `ClientHandler` reads a `Request` from a stream protected by a class allow-list.
2. `RequestRouter` resolves the session for the token **and** this connection.
3. `CommandPolicy.check` refuses the command unless the role has the permission. A command with no rule is refused.
4. The service validates input, runs one transaction, writes the audit entry in the same transaction.
5. A `Response` is returned. Unexpected errors become `INTERNAL_ERROR` with a generic message, details go to the server log.

## Key decisions

- **Server owns the numbers.** Clients send ids and quantities only. `PricingCalculator` (BigDecimal, HALF_UP) is the single source of prices, discounts, shipping, VAT. Rates come from `system_settings`.
- **No overselling.** Checkout locks the cart, locks inventory rows in id order, then uses `UPDATE inventory SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?`. The table also has `CHECK (quantity >= 0)`.
- **No double orders.** `(user_id, idempotency_key)` is unique. A repeated checkout returns the original order. A partial unique index allows only one PAID payment per order.
- **History is kept.** Order items copy SKU, name and price at purchase time. Products are archived, not deleted.
- **Sessions.** Random 32 byte tokens bound to the TCP connection, idle timeout, ended on disconnect, suspension or role change.
- **Roles.** SUPER_ADMIN cannot be assigned through the protocol. No self changes. An ADMIN manages customers only.
- **Payment boundary.** `PaymentGateway` is an interface. `SimulatedPaymentGateway` is the only implementation.
- **Audit.** ADMIN and SECURITY events. Refused actions are logged in a separate transaction so the log survives the rollback. Administrators do not see SECURITY entries.
