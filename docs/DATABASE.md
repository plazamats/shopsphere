# Database

PostgreSQL. Create with `database/schema.sql`, fill with `database/seed.sql`. ER diagram: `docs/er-diagram.png`
(source `docs/er-diagram.mmd`).

Tables: roles, users, user_roles, addresses, categories, products, product_images, inventory, stock_movements,
carts, cart_items, orders, order_items, payments, reviews, wishlists, wishlist_items, audit_logs, system_settings.

Highlights:
- Money is `NUMERIC(12,2)`. Timestamps are `TIMESTAMPTZ`.
- `users`: unique `lower(email)`, failed attempt counter and lock time.
- `inventory`: `CHECK (quantity >= 0)`.
- `orders`: unique `order_number` from a sequence, unique `(user_id, idempotency_key)`, shipping address copied in.
- `order_items`: SKU, name and unit price copied at purchase time.
- `payments`: partial unique index, one PAID or REFUNDED payment per order. Only `card_last4` is stored.
- `reviews`: unique `(user_id, product_id)`.
- Foreign keys everywhere, plus indexes on lookup columns.

All SQL in the DAOs uses PreparedStatement with try-with-resources. Search text is escaped for LIKE.
