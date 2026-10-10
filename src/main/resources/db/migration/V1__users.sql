-- M1: authentication foundation.
-- Users table with a UNIQUE email (which PostgreSQL backs with an index,
-- so no separate email index is needed) and a role checked by the app layer.
-- Passwords are stored as BCrypt hashes, never plaintext.

CREATE TABLE users (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email         VARCHAR(120) NOT NULL UNIQUE,
  password_hash TEXT         NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'SELLER', 'ADMIN'))
);
