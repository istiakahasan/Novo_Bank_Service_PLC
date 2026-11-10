-- 008_treasury.sql: Treasury/ALM Service schema for liquidity position, NIM, and interest rate gap analysis

CREATE TABLE IF NOT EXISTS treasury_snapshots (
  snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  snapshot_date DATE NOT NULL,
  total_deposits_cents BIGINT NOT NULL,
  total_loans_cents BIGINT NOT NULL,
  liquid_assets_cents BIGINT NOT NULL,
  lcr_ratio NUMERIC(6,4),
  nim_bps INT,
  created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE(snapshot_date)
);

CREATE TABLE IF NOT EXISTS alm_gap_buckets (
  bucket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  snapshot_id UUID REFERENCES treasury_snapshots(snapshot_id),
  bucket_label VARCHAR(20) NOT NULL,
  assets_cents BIGINT NOT NULL,
  liabilities_cents BIGINT NOT NULL,
  gap_cents BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_treasury_snapshots_date ON treasury_snapshots(snapshot_date);
CREATE INDEX IF NOT EXISTS idx_alm_gap_buckets_snapshot ON alm_gap_buckets(snapshot_id);
