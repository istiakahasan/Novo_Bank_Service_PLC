-- =====================================================================================================
-- 012_loan_collateral_collections.sql: Collateral tracking and collections/recovery workflows
-- Run in Supabase SQL Editor. Safe to re-run.
-- =====================================================================================================

CREATE TABLE IF NOT EXISTS loan_collateral (
  collateral_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES loan_applications(application_id),
  collateral_type VARCHAR(30) NOT NULL CHECK (collateral_type IN ('PROPERTY','VEHICLE','FIXED_DEPOSIT','SECURITIES','OTHER')),
  description VARCHAR(500) NOT NULL,
  estimated_value_cents BIGINT NOT NULL,
  valuation_date DATE NOT NULL,
  status VARCHAR(20) DEFAULT 'PLEDGED' CHECK (status IN ('PLEDGED','RELEASED','SEIZED')),
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_collateral_app ON loan_collateral(application_id);

CREATE TABLE IF NOT EXISTS collection_actions (
  action_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES loan_applications(application_id),
  repayment_id UUID REFERENCES loan_repayments(repayment_id),
  action_type VARCHAR(30) NOT NULL CHECK (action_type IN ('REMINDER_SENT','PHONE_CALL','DEMAND_LETTER','LEGAL_NOTICE','WRITE_OFF_PROPOSED','WRITE_OFF_APPROVED')),
  performed_by UUID,
  notes VARCHAR(500),
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_collection_app ON collection_actions(application_id);

ALTER TABLE loan_repayments DROP CONSTRAINT IF EXISTS loan_repayments_status_check;
ALTER TABLE loan_repayments ADD CONSTRAINT loan_repayments_status_check CHECK (status IN ('PENDING','PAID','OVERDUE','WRITTEN_OFF'));
