-- 009_reporting.sql: Reporting Service schema for asynchronous report jobs and extracts

CREATE TABLE IF NOT EXISTS report_jobs (
  job_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  report_type VARCHAR(30) NOT NULL,
  customer_id UUID,
  period_start DATE NOT NULL,
  period_end DATE NOT NULL,
  status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','GENERATING','COMPLETED','FAILED')),
  file_url TEXT,
  error_message VARCHAR(500),
  requested_by UUID,
  created_at TIMESTAMPTZ DEFAULT now(),
  completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_report_jobs_customer ON report_jobs(customer_id);
CREATE INDEX IF NOT EXISTS idx_report_jobs_status_created ON report_jobs(status, created_at ASC);
