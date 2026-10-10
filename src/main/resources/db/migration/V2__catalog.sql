-- M2: catalog. Products belong to exactly one category; each product has
-- exactly one inventory row (unique product_id). Indexes match the read paths:
-- name trigram/ILIKE search is unindexed at this scale (documented), while
-- category filtering and the inventory join use b-tree indexes below.

CREATE TABLE categories (
  id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE products (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        VARCHAR(150) NOT NULL,
  description VARCHAR(2000),
  price       NUMERIC(12, 2) NOT NULL CONSTRAINT chk_products_price CHECK (price > 0),
  category_id BIGINT NOT NULL REFERENCES categories (id),
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id BIGINT NOT NULL UNIQUE REFERENCES products (id) ON DELETE CASCADE,
  quantity   INTEGER NOT NULL DEFAULT 0 CONSTRAINT chk_inventory_qty CHECK (quantity >= 0),
  version    BIGINT NOT NULL DEFAULT 0
);

-- Read-path indexes (and why):
-- 1. Listing by category: WHERE category_id = ? (+ pagination).
CREATE INDEX idx_products_category ON products (category_id);
-- 2. Active-only storefront lists: partial index keeps it small.
CREATE INDEX idx_products_active ON products (active) WHERE active = TRUE;
-- 3. Inventory join per product page: FK lookups.
CREATE INDEX idx_inventory_product ON inventory (product_id);
-- 4. Newest-first / price sorts: created_at ordering.
CREATE INDEX idx_products_created ON products (created_at DESC);
-- Deliberately NO index on name ILIKE search: pattern '%q%' can't use b-trees;
-- a trigram index arrives in M5 only if search becomes a measured bottleneck.
-- Write tradeoff: each index slows product inserts/updates slightly; four narrow
-- indexes are cheap relative to the read-heavy storefront workload.
