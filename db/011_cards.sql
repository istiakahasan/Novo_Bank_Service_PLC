-- 011_cards.sql: Schema for virtual debit cards and four-stage settlement simulation

CREATE TABLE IF NOT EXISTS cards (
  card_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL,
  account_id UUID NOT NULL,
  card_number_masked VARCHAR(20) NOT NULL,
  card_number_hash VARCHAR(64) UNIQUE NOT NULL,
  expiry_month INT NOT NULL,
  expiry_year INT NOT NULL,
  cvv_hash VARCHAR(64) NOT NULL,
  status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','CANCELLED','EXPIRED')),
  daily_limit_cents BIGINT NOT NULL DEFAULT 500000,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cards_customer ON cards(customer_id);
CREATE INDEX IF NOT EXISTS idx_cards_account ON cards(account_id);

CREATE TABLE IF NOT EXISTS card_transactions (
  card_txn_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  card_id UUID NOT NULL REFERENCES cards(card_id),
  merchant_name VARCHAR(100) NOT NULL,
  merchant_category VARCHAR(10),
  amount_cents BIGINT NOT NULL,
  stage VARCHAR(20) NOT NULL DEFAULT 'AUTHORIZED' CHECK (stage IN ('AUTHORIZED','BATCHED','CLEARED','FUNDED','DECLINED','REVERSED')),
  authorization_code VARCHAR(10),
  ledger_hold_id UUID,
  ledger_transaction_id UUID,
  created_at TIMESTAMPTZ DEFAULT now(),
  settled_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_card_txn_card ON card_transactions(card_id);
CREATE INDEX IF NOT EXISTS idx_card_txn_stage ON card_transactions(stage);
