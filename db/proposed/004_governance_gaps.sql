-- PROPOSED migration from docs/DATA_GOVERNANCE.md (Deliverable 7). NOT applied automatically and NOT required
-- by the current code (all new columns are nullable or defaulted, so ddl-auto=validate keeps passing).
-- Review with compliance, then run in the Supabase SQL Editor. Safe to re-run.

-- G1  Address (FATF R.10 / BFIU KYC): the API validates addressLine1 today but discards it.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address_line_1 VARCHAR(200);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS address_line_2 VARCHAR(200);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS city           VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS district       VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS postal_code    VARCHAR(20);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS country        CHAR(2) DEFAULT 'BD';

-- G2  Who verified KYC and when (FATF R.10/R.11 accountability; GDPR Art.5(2)). verifiedBy is discarded today.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS kyc_verified_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS kyc_verified_by UUID;          -- auth_users.id of the STAFF member

-- G3  Audit trail (GDPR Art.5(2); Bangladesh Bank ICT security guideline audit logging)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS updated_by UUID;

-- G4  PEP and sanctions (FATF R.12, R.6)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_pep               BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS sanctions_screened_at TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS sanctions_match       BOOLEAN;

-- G5  Document validity and periodic KYC review (FATF R.10(d) ongoing due diligence)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS id_issue_date          DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS id_expiry_date         DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS id_issuing_authority   VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS kyc_review_due_at      TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS kyc_expiry_alert_sent_at TIMESTAMPTZ;

-- G6  Lifecycle and retention clock (MLPA 2012 s.25: records kept 5 years after account closure; GDPR Art.5(1)(e))
ALTER TABLE customers ADD COLUMN IF NOT EXISTS onboarding_date       DATE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS activated_at          TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS closed_at             TIMESTAMPTZ;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS closure_reason        VARCHAR(100);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS retention_until       DATE;        -- closed_at + 5 years
ALTER TABLE customers ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ; -- GDPR Art.17 request received
ALTER TABLE customers ADD COLUMN IF NOT EXISTS anonymized_at         TIMESTAMPTZ;

-- G7  Lawful basis and purpose per customer (GDPR Art.5(1)(b), Art.6, Art.30(1)(b))
ALTER TABLE customers ADD COLUMN IF NOT EXISTS processing_purpose_code VARCHAR(30) DEFAULT 'ACCOUNT_SERVICING';

-- G8  Searchable hash so id_number can be stored encrypted at rest (GDPR Art.32; BB ICT guideline)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS id_number_hash CHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_customers_id_number_hash ON customers(id_number_hash) WHERE id_number_hash IS NOT NULL;

-- G9  Outbox keeps every payload forever: record delivery time so a purge job can delete delivered rows
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

-- G10 Fraud alerts: retention clock and review outcome (FATF R.11; investigations may extend the hold)
ALTER TABLE fraud_alerts ADD COLUMN IF NOT EXISTS reviewed_at  TIMESTAMPTZ;
ALTER TABLE fraud_alerts ADD COLUMN IF NOT EXISTS legal_hold   BOOLEAN NOT NULL DEFAULT false;

-- G11 Login identity: cap on personal data in auth_users (GDPR Art.5(1)(c)) and last-login for dormant-account cleanup
ALTER TABLE auth_users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- G12 Consents (GDPR Art.7(1) proof of consent, Art.7(3) withdrawal)
CREATE TABLE IF NOT EXISTS customer_consents (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id      UUID NOT NULL REFERENCES customers(id),
  consent_type     VARCHAR(40) NOT NULL,          -- MARKETING_EMAIL, SMS_ALERTS, DATA_SHARING, ...
  consent_version  VARCHAR(20) NOT NULL,          -- version of the wording the customer saw
  purpose_code     VARCHAR(30) NOT NULL,
  consent_given    BOOLEAN     NOT NULL,
  given_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at       TIMESTAMPTZ,
  ip_address       INET,
  user_agent       VARCHAR(300),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_consents_customer ON customer_consents(customer_id);

-- G13 KYC documents (FATF R.10 verify using reliable documents; images stored OUTSIDE the DB, only a reference here)
CREATE TABLE IF NOT EXISTS customer_documents (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id         UUID NOT NULL REFERENCES customers(id),
  document_type       VARCHAR(30) NOT NULL,
  document_number_hash CHAR(64),                  -- the number itself is held encrypted by the application
  front_image_url     VARCHAR(500),               -- private storage path, never a public link
  back_image_url      VARCHAR(500),
  is_primary          BOOLEAN NOT NULL DEFAULT false,
  verification_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  expires_on          DATE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_documents_customer ON customer_documents(customer_id);

-- G14 Who looked at personal data (GDPR Art.5(1)(f), Art.32; BB ICT guideline)
CREATE TABLE IF NOT EXISTS pii_access_log (
  id          BIGSERIAL PRIMARY KEY,
  actor       VARCHAR(100) NOT NULL,              -- auth_users.id or service id
  actor_role  VARCHAR(20)  NOT NULL,
  customer_id UUID,
  field_group VARCHAR(40)  NOT NULL,              -- e.g. CONTACT, IDENTITY, KYC
  action      VARCHAR(20)  NOT NULL,              -- READ, UPDATE, EXPORT
  at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

ALTER TABLE customer_consents  ENABLE ROW LEVEL SECURITY;
ALTER TABLE customer_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE pii_access_log     ENABLE ROW LEVEL SECURITY;
