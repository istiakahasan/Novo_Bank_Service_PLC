-- Novo Bank Service PLC - schema. Paste whole file into Supabase SQL Editor and click Run.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS customers (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_number  VARCHAR(20)  UNIQUE NOT NULL,
  first_name       VARCHAR(100) NOT NULL,
  last_name        VARCHAR(100) NOT NULL,
  display_name     VARCHAR(200),
  date_of_birth    DATE,
  mobile_no        VARCHAR(20)  UNIQUE NOT NULL,
  email            VARCHAR(150) UNIQUE NOT NULL,
  id_type          VARCHAR(30),
  id_number        VARCHAR(50)  UNIQUE,
  kyc_status       VARCHAR(20)  NOT NULL DEFAULT 'pending',
  aml_risk_rating  VARCHAR(20)  NOT NULL DEFAULT 'low',
  status           VARCHAR(20)  NOT NULL DEFAULT 'active',
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id     UUID NOT NULL REFERENCES customers(id),
  account_number  VARCHAR(30) UNIQUE NOT NULL,
  account_type    VARCHAR(20) NOT NULL DEFAULT 'savings',
  balance_cents   BIGINT NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
  status          VARCHAR(20) NOT NULL DEFAULT 'active',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key  VARCHAR(100) UNIQUE NOT NULL,
  from_account     UUID NOT NULL REFERENCES accounts(id),
  to_account       UUID NOT NULL REFERENCES accounts(id),
  amount_cents     BIGINT NOT NULL CHECK (amount_cents > 0),
  status           VARCHAR(20) NOT NULL DEFAULT 'pending',
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ledger_entries (
  id              BIGSERIAL PRIMARY KEY,
  transaction_id  UUID NOT NULL REFERENCES transactions(id),
  account_id      UUID NOT NULL REFERENCES accounts(id),
  direction       VARCHAR(6) NOT NULL CHECK (direction IN ('debit','credit')),
  amount_cents    BIGINT NOT NULL CHECK (amount_cents > 0),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbox (
  id          BIGSERIAL PRIMARY KEY,
  event_type  VARCHAR(100) NOT NULL,
  payload     JSONB NOT NULL,
  published   BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_customers_mobile_no   ON customers(mobile_no);
CREATE INDEX IF NOT EXISTS idx_customers_email       ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_id_number   ON customers(id_number);
CREATE INDEX IF NOT EXISTS idx_transactions_idem_key ON transactions(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_accounts_customer_id  ON accounts(customer_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account_id     ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_outbox_unpublished    ON outbox(id) WHERE published = false;

-- Ledger is append-only: UPDATE / DELETE are silently ignored
CREATE OR REPLACE RULE ledger_no_update AS ON UPDATE TO ledger_entries DO INSTEAD NOTHING;
CREATE OR REPLACE RULE ledger_no_delete AS ON DELETE TO ledger_entries DO INSTEAD NOTHING;

-- Balance functions
CREATE OR REPLACE FUNCTION increment_balance(acc_id UUID, amt BIGINT)
RETURNS VOID LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF amt <= 0 THEN RAISE EXCEPTION 'amount must be positive'; END IF;
  UPDATE accounts SET balance_cents = balance_cents + amt, updated_at = now() WHERE id = acc_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'account % not found', acc_id; END IF;
END $$;

CREATE OR REPLACE FUNCTION decrement_balance(acc_id UUID, amt BIGINT)
RETURNS VOID LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF amt <= 0 THEN RAISE EXCEPTION 'amount must be positive'; END IF;
  UPDATE accounts SET balance_cents = balance_cents - amt, updated_at = now() WHERE id = acc_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'account % not found', acc_id; END IF;
  -- CHECK (balance_cents >= 0) rejects overdrafts automatically
END $$;

-- Block Supabase's public REST API from these tables (backend uses a direct DB connection)
ALTER TABLE customers      ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE transactions   ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox         ENABLE ROW LEVEL SECURITY;
