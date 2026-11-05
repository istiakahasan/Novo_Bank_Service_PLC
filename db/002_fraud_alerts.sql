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
ALTER TABLE fraud_alerts ENABLE ROW LEVEL SECURITY;
