-- Run in Supabase SQL Editor (after schema.sql / seed.sql). Safe to re-run.
CREATE TABLE IF NOT EXISTS auth_users (
  id            UUID PRIMARY KEY,
  username      VARCHAR(100) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','STAFF')),
  customer_id   UUID REFERENCES customers(id),
  enabled       BOOLEAN      NOT NULL DEFAULT true,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT customer_role_needs_customer CHECK (role <> 'CUSTOMER' OR customer_id IS NOT NULL),
  CONSTRAINT staff_has_no_customer        CHECK (role <> 'STAFF'    OR customer_id IS NULL)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          UUID PRIMARY KEY,
  user_id     UUID        NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked     BOOLEAN     NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry ON refresh_tokens(expires_at);

ALTER TABLE auth_users     ENABLE ROW LEVEL SECURITY;
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;

-- Optional housekeeping (keeps the 500 MB DB small): delete expired refresh tokens
-- DELETE FROM refresh_tokens WHERE expires_at < now() - interval '1 day';
