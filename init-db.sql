-- =====================================================================================================
-- init-db.sql: local development database "banking" (single database, all services).
-- Mounted into /docker-entrypoint-initdb.d/ so it runs once, the first time the pgdata volume is created.
--
-- Tables (10): customers, accounts, transactions, ledger_entries, outbox      (customer + ledger app)
--              fraud_alerts                                                    (fraud-service)
--              support_tickets, ticket_messages                                (support-service)
--              auth_users, refresh_tokens                                      (auth-server, public schema locally)
-- It is the concatenation of db/schema.sql, db/002_fraud_alerts.sql, db/005_support.sql and
-- db/006_auth_server.sql (schema prefix removed, Supabase row-level-security lines left out),
-- followed by the seed data. Keep it in step with those files when a table changes.
-- =====================================================================================================

-- ---------------------------------------------------------------- customers, accounts, ledger, outbox
-- Novo Bank Service PLC - schema. Paste whole file into Supabase SQL Editor and click Run.

CREATE TABLE IF NOT EXISTS customers (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_number  VARCHAR(20)  UNIQUE NOT NULL,
  first_name       VARCHAR(100) NOT NULL,
  last_name        VARCHAR(100) NOT NULL,
  display_name     VARCHAR(200) NOT NULL,
  date_of_birth    DATE NOT NULL,
  mobile_no        VARCHAR(20)  UNIQUE NOT NULL,
  email            VARCHAR(150) UNIQUE,
  id_type          VARCHAR(30) NOT NULL,
  id_number        VARCHAR(50)  UNIQUE NOT NULL,
  kyc_status       VARCHAR(20)  NOT NULL DEFAULT 'pending',
  aml_risk_rating  VARCHAR(10)  NOT NULL DEFAULT 'low',
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

-- ---------------------------------------------------------------- fraud
-- Run in Supabase SQL Editor (after schema.sql). Safe to re-run.
CREATE TABLE IF NOT EXISTS fraud_alerts (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID,
  account_id     UUID,
  rule_fired     VARCHAR(50),
  severity       VARCHAR(20),
  details        JSONB,
  created_at     TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fraud_alerts_transaction ON fraud_alerts(transaction_id);

-- ---------------------------------------------------------------- support
-- Support Service tables. Run in the Supabase SQL Editor (after schema.sql). Safe to re-run.
CREATE TABLE IF NOT EXISTS support_tickets (
  ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL,
  assigned_agent_id UUID,
  category VARCHAR(30) NOT NULL CHECK (category IN ('TRANSACTION_DISPUTE','ACCOUNT_LOCK','KYC_VERIFICATION','GENERAL_INQUIRY')),
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','PENDING_CUSTOMER','RESOLVED','CLOSED')),
  subject VARCHAR(200) NOT NULL,
  related_transaction_id UUID,
  created_at TIMESTAMPTZ DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_tickets_customer ON support_tickets(customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON support_tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_agent ON support_tickets(assigned_agent_id);

CREATE TABLE IF NOT EXISTS ticket_messages (
  message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
  sender_id UUID NOT NULL,
  sender_type VARCHAR(10) NOT NULL CHECK (sender_type IN ('CUSTOMER','AGENT','SYSTEM')),
  message_body TEXT NOT NULL,
  is_internal_note BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_ticket ON ticket_messages(ticket_id, created_at);

-- append-only: UPDATE and DELETE become no-ops
CREATE OR REPLACE RULE no_update_messages AS ON UPDATE TO ticket_messages DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_messages AS ON DELETE TO ticket_messages DO INSTEAD NOTHING;

-- same convention as the other tables: block Supabase's public REST API, the backend uses a direct connection

-- ---------------------------------------------------------------- auth

CREATE TABLE IF NOT EXISTS auth_users (
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
CREATE INDEX IF NOT EXISTS idx_auth_users_email ON auth_users(email);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth_users(user_id) ON DELETE CASCADE,
  token_hash VARCHAR(64) UNIQUE NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);



-- =====================================================================================================
-- SEED DATA (local development only)
-- Password for alice@test.com, bob@test.com and staff@test.com is:  TestPass123!
-- The three values below are BCrypt (strength 12, $2a$12$...) hashes of exactly that password.
-- =====================================================================================================
INSERT INTO customers (id, customer_number, first_name, last_name, display_name, date_of_birth, mobile_no, email, id_type, id_number, kyc_status) VALUES
 ('11111111-1111-4111-8111-111111111111','CUST000001','Alice','Smith','Alice Smith','1990-01-15','+880170000001','alice@test.com','nid','NID-0000000001','verified'),
 ('22222222-2222-4222-8222-222222222222','CUST000002','Bob','Jones','Bob Jones','1988-06-20','+880170000002','bob@test.com','nid','NID-0000000002','verified');

INSERT INTO accounts (id, customer_id, account_number, account_type, balance_cents) VALUES
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','NOVO0000000001','savings',100000),
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','22222222-2222-4222-8222-222222222222','NOVO0000000002','savings',50000);

INSERT INTO auth_users (user_id, email, password_hash, role, customer_id, agent_id) VALUES
 ('a0000000-0000-4000-8000-000000000001','alice@test.com','$2a$12$/yqQ0SNcBsiuBc2SjYg1nuSCZ.82BVFBvgAnXB8Y2xHUiKY.K28SK','CUSTOMER','11111111-1111-4111-8111-111111111111',NULL),
 ('a0000000-0000-4000-8000-000000000002','bob@test.com','$2a$12$u2xe98arin.pqN/XCf4W.OA2iWX7sbDyF/.w5OPP..0/xs5wEGE6e','CUSTOMER','22222222-2222-4222-8222-222222222222',NULL),
 ('a0000000-0000-4000-8000-000000000003','staff@test.com','$2a$12$7nN2BeSv9SfDRM3tr9nSWOG5Yj4LnHINUpQT0W/Ayvp9E8XtOrWFy','STAFF',NULL,'dddddddd-dddd-4ddd-8ddd-dddddddddddd');

INSERT INTO support_tickets (ticket_id, customer_id, category, priority, status, subject) VALUES
 ('c0000000-0000-4000-8000-000000000001','11111111-1111-4111-8111-111111111111','GENERAL_INQUIRY','MEDIUM','OPEN','How do I update my mobile number?');

INSERT INTO ticket_messages (ticket_id, sender_id, sender_type, message_body) VALUES
 ('c0000000-0000-4000-8000-000000000001','00000000-0000-0000-0000-000000000000','SYSTEM','Ticket created'),
 ('c0000000-0000-4000-8000-000000000001','11111111-1111-4111-8111-111111111111','CUSTOMER','Hi, I changed my SIM card. How can I update the mobile number on my profile?');
