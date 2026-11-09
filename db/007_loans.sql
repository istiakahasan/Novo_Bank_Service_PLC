-- =====================================================================================================
-- Loan Service tables. Run in Supabase SQL Editor. Safe to re-run.
-- =====================================================================================================

CREATE TABLE IF NOT EXISTS loan_products (
  product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_code VARCHAR(20) UNIQUE NOT NULL,
  product_name VARCHAR(100) NOT NULL,
  min_amount_cents BIGINT NOT NULL,
  max_amount_cents BIGINT NOT NULL,
  min_tenure_months INT NOT NULL,
  max_tenure_months INT NOT NULL,
  base_interest_rate_bps INT NOT NULL,
  is_active BOOLEAN DEFAULT true,
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS loan_applications (
  application_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL,
  product_id UUID NOT NULL REFERENCES loan_products(product_id),
  requested_amount_cents BIGINT NOT NULL,
  requested_tenure_months INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED','DISBURSED','CLOSED')),
  approved_amount_cents BIGINT,
  approved_interest_rate_bps INT,
  approved_tenure_months INT,
  rejection_reason VARCHAR(500),
  reviewed_by UUID,
  reviewed_at TIMESTAMPTZ,
  disbursed_at TIMESTAMPTZ,
  disbursement_transaction_id UUID,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_loan_app_customer ON loan_applications(customer_id);
CREATE INDEX IF NOT EXISTS idx_loan_app_status ON loan_applications(status);

CREATE TABLE IF NOT EXISTS loan_repayments (
  repayment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES loan_applications(application_id),
  due_date DATE NOT NULL,
  principal_due_cents BIGINT NOT NULL,
  interest_due_cents BIGINT NOT NULL,
  total_due_cents BIGINT NOT NULL,
  paid_at TIMESTAMPTZ,
  payment_transaction_id UUID,
  status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID','OVERDUE')),
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_repayment_app ON loan_repayments(application_id, due_date);

INSERT INTO loan_products (product_code, product_name, min_amount_cents, max_amount_cents, min_tenure_months, max_tenure_months, base_interest_rate_bps) VALUES
('PERSONAL', 'Personal Loan', 5000000, 50000000, 6, 60, 1200),
('AUTO', 'Auto Loan', 10000000, 200000000, 12, 84, 900),
('HOME', 'Home Loan', 50000000, 1000000000, 60, 240, 750)
ON CONFLICT (product_code) DO NOTHING;
