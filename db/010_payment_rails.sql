-- 010_payment_rails.sql: Schema for external payment rails simulation (ACH, RTGS, SWIFT_SIM)

CREATE TABLE IF NOT EXISTS external_payments (
  payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  direction VARCHAR(10) NOT NULL CHECK (direction IN ('OUTBOUND','INBOUND')),
  rail VARCHAR(20) NOT NULL CHECK (rail IN ('ACH','RTGS','SWIFT_SIM')),
  customer_id UUID,
  internal_account_id UUID,
  external_account_number VARCHAR(50),
  external_routing_number VARCHAR(20),
  external_bank_name VARCHAR(100),
  amount_cents BIGINT NOT NULL,
  currency VARCHAR(3) DEFAULT 'USD',
  status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','RETURNED')),
  ledger_transaction_id UUID,
  failure_reason VARCHAR(200),
  scheduled_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  idempotency_key VARCHAR(100) UNIQUE,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ext_pay_customer ON external_payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_ext_pay_status ON external_payments(status);
