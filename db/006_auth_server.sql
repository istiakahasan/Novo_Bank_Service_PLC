-- Auth Service tables. Run in the Supabase SQL Editor. Safe to re-run.
-- They live in their own schema (auth_v2) so they can never collide with the auth_users / refresh_tokens
-- tables of the older auth-service (db/003_auth.sql). To use the public schema instead, delete the three
-- "auth_v2." prefixes below and set AUTH_DB_SCHEMA=public on the service.
CREATE SCHEMA IF NOT EXISTS auth_v2;

CREATE TABLE IF NOT EXISTS auth_v2.auth_users (
  user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(150) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','STAFF','SYSTEM')),
  customer_id UUID,
  agent_id UUID,
  is_active BOOLEAN NOT NULL DEFAULT true,
  failed_login_attempts INT NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_auth_users_email ON auth_v2.auth_users(email);

CREATE TABLE IF NOT EXISTS auth_v2.refresh_tokens (
  token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth_v2.auth_users(user_id) ON DELETE CASCADE,
  token_hash VARCHAR(64) UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON auth_v2.refresh_tokens(user_id);

-- same convention as the other tables: no access through Supabase's public REST API
ALTER TABLE auth_v2.auth_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE auth_v2.refresh_tokens ENABLE ROW LEVEL SECURITY;
