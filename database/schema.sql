CREATE TABLE roles (
    id    SMALLINT    PRIMARY KEY,
    name  VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE users (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(254) NOT NULL,
    first_name      VARCHAR(60)  NOT NULL,
    last_name       VARCHAR(60)  NOT NULL,
    phone           VARCHAR(20),
    password_hash   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    failed_attempts INTEGER      NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    locked_until    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);
/* Emails are unique ignoring case. */
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE user_roles (
    user_id  BIGINT   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id  SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX idx_user_roles_role ON user_roles (role_id);

CREATE TABLE addresses (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient   VARCHAR(100) NOT NULL,
    line1       VARCHAR(120) NOT NULL,
    line2       VARCHAR(120),
    city        VARCHAR(80)  NOT NULL,
    province    VARCHAR(80)  NOT NULL,
    postal_code VARCHAR(12)  NOT NULL,
    country     VARCHAR(60)  NOT NULL DEFAULT 'South Africa',
    phone       VARCHAR(20),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user ON addresses (user_id);

CREATE TABLE categories (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE products (
    id          BIGSERIAL     PRIMARY KEY,
    sku         VARCHAR(30)   NOT NULL UNIQUE,
    name        VARCHAR(150)  NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price > 0),
    category_id INTEGER       REFERENCES categories (id),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,   /* FALSE means archived */
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_name ON products (lower(name));

CREATE TABLE product_images (
    id         BIGSERIAL    PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url  VARCHAR(500) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_product_images_product ON product_images (product_id);

CREATE TABLE inventory (
    product_id          BIGINT      PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    quantity            INTEGER     NOT NULL CHECK (quantity >= 0),      /* stock can never go negative */
    low_stock_threshold INTEGER     NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stock_movements (
    id            BIGSERIAL    PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    change_qty    INTEGER      NOT NULL CHECK (change_qty <> 0),
    resulting_qty INTEGER      NOT NULL CHECK (resulting_qty >= 0),
    reason        VARCHAR(20)  NOT NULL
                  CHECK (reason IN ('INITIAL','PURCHASE','CANCELLATION','PAYMENT_FAILED','RESTOCK','ADJUSTMENT','DAMAGE','CORRECTION')),
    reference     VARCHAR(40),
    note          VARCHAR(255),
    created_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_movements_product ON stock_movements (product_id, created_at DESC);

CREATE TABLE carts (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products (id),
    quantity   INTEGER     NOT NULL CHECK (quantity BETWEEN 1 AND 1000),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cart_id, product_id)
);

CREATE SEQUENCE order_number_seq START 1000;

CREATE TABLE orders (
    id              BIGSERIAL     PRIMARY KEY,
    order_number    VARCHAR(30)   NOT NULL UNIQUE,
    user_id         BIGINT        NOT NULL REFERENCES users (id),
    status          VARCHAR(20)   NOT NULL
                    CHECK (status IN ('PENDING_PAYMENT','PAYMENT_FAILED','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    subtotal        NUMERIC(12,2) NOT NULL CHECK (subtotal >= 0),
    discount        NUMERIC(12,2) NOT NULL CHECK (discount >= 0),
    shipping        NUMERIC(12,2) NOT NULL CHECK (shipping >= 0),
    tax             NUMERIC(12,2) NOT NULL CHECK (tax >= 0),
    total           NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    /* the shipping address is copied so later address changes do not alter history */
    ship_recipient  VARCHAR(100)  NOT NULL,
    ship_line1      VARCHAR(120)  NOT NULL,
    ship_line2      VARCHAR(120),
    ship_city       VARCHAR(80)   NOT NULL,
    ship_province   VARCHAR(80)   NOT NULL,
    ship_postal     VARCHAR(12)   NOT NULL,
    ship_country    VARCHAR(60)   NOT NULL,
    ship_phone      VARCHAR(20),
    idempotency_key VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_user_key UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_orders_user ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders (status);

/* Product name, SKU and price are copied here so history stays correct if the product changes. */
CREATE TABLE order_items (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT        NOT NULL REFERENCES products (id),
    sku          VARCHAR(30)   NOT NULL,
    product_name VARCHAR(150)  NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price > 0),
    quantity     INTEGER       NOT NULL CHECK (quantity > 0),
    line_total   NUMERIC(12,2) NOT NULL CHECK (line_total > 0)
);
CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

CREATE TABLE payments (
    id             BIGSERIAL     PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES orders (id),
    reference      VARCHAR(40)   NOT NULL UNIQUE,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('PAID','FAILED','REFUNDED')),
    method         VARCHAR(30)   NOT NULL DEFAULT 'SIMULATED_CARD',
    card_last4     VARCHAR(4),
    simulated      BOOLEAN       NOT NULL DEFAULT TRUE,   /* Version 1 never moves real money */
    failure_reason VARCHAR(120),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_order ON payments (order_id);
/* Duplicate payment protection: an order can have at most one successful (or refunded) payment. */
CREATE UNIQUE INDEX uq_payments_one_paid_per_order ON payments (order_id) WHERE status IN ('PAID', 'REFUNDED');

CREATE TABLE reviews (
    id                BIGSERIAL    PRIMARY KEY,
    product_id        BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    user_id           BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating            SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment           VARCHAR(1000),
    verified_purchase BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)
);
CREATE INDEX idx_reviews_product ON reviews (product_id);

CREATE TABLE wishlists (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE wishlist_items (
    wishlist_id BIGINT      NOT NULL REFERENCES wishlists (id) ON DELETE CASCADE,
    product_id  BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (wishlist_id, product_id)
);

CREATE TABLE audit_logs (
    id          BIGSERIAL    PRIMARY KEY,
    actor_id    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    actor_email VARCHAR(254),
    action      VARCHAR(60)  NOT NULL,
    category    VARCHAR(20)  NOT NULL CHECK (category IN ('ADMIN','SECURITY','SYSTEM')),
    entity_type VARCHAR(40),
    entity_id   VARCHAR(40),
    details     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_category ON audit_logs (category, created_at DESC);

/* Business rules that SUPER_ADMIN may change without a new release. */
CREATE TABLE system_settings (
    setting_key   VARCHAR(60)  PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL,
    description   VARCHAR(255),
    updated_by    BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
