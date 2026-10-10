-- M3: cart + orders + payments. One cart per user (unique user_id); order
-- items snapshot the price so catalog edits never rewrite history; payments
-- carry a unique gateway-style reference; idempotency keys are scoped per user.

CREATE TABLE carts (
  id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  cart_id    BIGINT NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products (id),
  quantity   INTEGER NOT NULL CONSTRAINT chk_cart_qty CHECK (quantity > 0),
  CONSTRAINT uq_cart_product UNIQUE (cart_id, product_id)
);

CREATE TABLE orders (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         BIGINT NOT NULL REFERENCES users (id),
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  total           NUMERIC(12, 2) NOT NULL CONSTRAINT chk_orders_total CHECK (total >= 0),
  idempotency_key VARCHAR(80) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_orders_user_key UNIQUE (user_id, idempotency_key),
  CONSTRAINT chk_orders_status CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED'))
);

CREATE TABLE order_items (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id   BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products (id),
  quantity   INTEGER NOT NULL CONSTRAINT chk_order_item_qty CHECK (quantity > 0),
  unit_price NUMERIC(12, 2) NOT NULL CONSTRAINT chk_order_item_price CHECK (unit_price >= 0)
);

CREATE TABLE payments (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id   BIGINT NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
  status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reference  VARCHAR(60) NOT NULL UNIQUE,
  amount     NUMERIC(12, 2) NOT NULL CONSTRAINT chk_payments_amount CHECK (amount >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_payments_status CHECK (status IN ('PENDING','SUCCESS','FAILED'))
);

-- Read-path indexes (and why):
-- 1. "My cart / my orders" lookups keyed by user.
CREATE INDEX idx_carts_user ON carts (user_id);
CREATE INDEX idx_orders_user ON orders (user_id);
-- 2. Line lookups per cart/order page.
CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);
CREATE INDEX idx_order_items_order ON order_items (order_id);
-- 3. Idempotency check on every checkout: WHERE user_id=? AND idempotency_key=?.
--    Covered by the uq_orders_user_key backing index; no extra index needed.
-- 4. Payment-by-order webhook lookups.
CREATE INDEX idx_payments_order ON payments (order_id);
