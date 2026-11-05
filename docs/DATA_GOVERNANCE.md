# Customer data governance inventory

Scope: every customer-related field stored or transmitted by the Novo Bank prototype (main app = Customer + Ledger, fraud-service, notification-service, auth-service, Redis, QStash). One consolidated inventory, not a per-service list.

## How to read this document (important)

1. **The brief's schema is larger than the real one.** The system as built stores only the fields marked **Implemented**. Everything the brief lists that is not in the code (`customer_documents`, `customer_consents`, address, PEP, sanctions, lifecycle and audit fields, ...) is marked **NOT IMPLEMENTED** and treated as *proposed*, with the rule that will apply when it is built. The proposed DDL is `db/proposed/004_governance_gaps.sql` (not applied automatically). This document does not pretend those tables exist.
2. **No retention or deletion job exists today.** The retention periods below are the target policy. Nothing in the code deletes or anonymizes anything yet (Deliverable 7, gap S7).
3. **Regulatory citations.** FATF Recommendations, GDPR articles and PCI DSS requirement families are cited at the level I am confident of. Bangladesh instruments are cited by name and section (Money Laundering Prevention Act 2012 s.25; BFIU master circulars; Bangladesh Bank ICT Security Guideline; Anti-Terrorism Act 2009; Contract Act 1872 s.11). **Circular numbers and clause numbers marked `[verify]`, and every Bangladesh citation, must be checked against the current text by compliance counsel before this is relied on.** GDPR applies if EU residents' data is processed or if the bank adopts it as policy; the status of a Bangladesh data-protection statute should be confirmed by counsel.
4. **Sensitivity scale.** Restricted = direct identifiers, government IDs, contact data, credentials, AML flags (leak causes harm or legal exposure). Confidential = names, financial data, KYC state, account numbers. Internal = opaque ids, enums, timestamps. Public = none of the customer fields.
5. Short names: *Customer*, *Ledger* (both in the main app), *Fraud*, *Notification*, *Auth*. `MLPA 2012` = Money Laundering Prevention Act 2012. `BFIU` = Bangladesh Financial Intelligence Unit. Masking functions are in `MaskingUtil` (Deliverable 6).

---

## Deliverable 1: master customer field inventory

116 rows: 74 implemented, 42 not implemented (proposed). Each field appears exactly once.


**A) Identity**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `id` | UUID | customers.id | Customer | Customer, Ledger, Fraud, Notification, Auth | Internal | GDPR Art.32 (opaque key instead of a name); Art.5(2) record key | Relationship + 5 yrs after closure, then anonymize | None (opaque); log first 8 chars | Implemented |
| 2 | `customer_number` | VARCHAR(20) | customers.customer_number | Customer | Customer, Notification (welcome email) | Confidential | FATF R.11; MLPA 2012 s.25(1) (unique customer record reference) | Relationship + 5 yrs after closure, then anonymize | maskAccountNumber -> CIF-**0004 | Implemented |
| 3 | `first_name` | VARCHAR(100) | customers.first_name | Customer | Customer | Confidential | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (legal name) | Relationship + 5 yrs after closure, then anonymize | maskName -> R*** | Implemented |
| 4 | `middle_name` | VARCHAR(100) | customers.middle_name (proposed) | - | - | Confidential | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (name as on ID) | Relationship + 5 yrs after closure, then anonymize | maskName | NOT IMPLEMENTED |
| 5 | `last_name` | VARCHAR(100) | customers.last_name | Customer | Customer | Confidential | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (legal name) | Relationship + 5 yrs after closure, then anonymize | maskName | Implemented |
| 6 | `display_name` | VARCHAR(200) | customers.display_name | Customer | Customer, Notification (email greeting), Fraud (event only) | Confidential | None: derived from first + last name; GDPR Art.5(1)(c) says do not duplicate data | Relationship + 5 yrs after closure, then anonymize | maskName | Implemented |
| 7 | `date_of_birth` | DATE | customers.date_of_birth | Customer | Customer (never returned by any API) | Restricted | FATF R.10 (identity + legal age); Contract Act 1872 s.11 / Majority Act 1875 (minors cannot contract) | Relationship + 5 yrs after closure, then anonymize | maskDateOfBirth -> 1992-**-** | Implemented |
| 8 | `gender` | VARCHAR(10) | customers.gender (proposed) | - | - | Confidential | None: collect only if the BFIU KYC form mandates it (GDPR Art.5(1)(c)) | Relationship + 5 yrs after closure, then anonymize | None (enum); never log with name | NOT IMPLEMENTED |
| 9 | `nationality` | CHAR(2) | customers.nationality (proposed) | - | - | Confidential | FATF R.10 and R.19 (country risk), R.6 (sanctions screening) | Relationship + 5 yrs after closure, then anonymize | None (country code) | NOT IMPLEMENTED |
| 10 | `id_type` | VARCHAR(30) | customers.id_type | Customer | Customer | Confidential | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (acceptable identity documents) | Relationship + 5 yrs after closure, then anonymize | None (enum) | Implemented |
| 11 | `id_number` | VARCHAR(50) | customers.id_number | Customer | Customer (duplicate check only) | Restricted | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (identity verification record) | Relationship + 5 yrs after closure, then anonymize | maskIdNumber -> ****-****-7890 | Implemented |
| 12 | `id_issue_date` | DATE | customers.id_issue_date (proposed) | - | - | Confidential | FATF R.10 (verify with reliable, valid documents) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 13 | `id_expiry_date` | DATE | customers.id_expiry_date (proposed) | - | - | Confidential | FATF R.10(d) ongoing due diligence (expired ID triggers re-verification) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 14 | `id_issuing_authority` | VARCHAR(100) | customers.id_issuing_authority (proposed) | - | - | Internal | FATF R.10 (document provenance) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |

**B) Contact**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 15 | `email` | VARCHAR(150) | customers.email | Customer | Customer, Notification (email + Redis cache), Fraud (event payload) | Restricted | BFIU KYC profile (contact details); GDPR Art.6(1)(b) (account notifications) | Relationship + 5 yrs after closure, then anonymize | maskEmail -> r***@example.com | Implemented |
| 16 | `mobile_no` | VARCHAR(20) | customers.mobile_no | Customer | Customer, Notification (SMS stub, Redis cache), Fraud (event payload) | Restricted | BFIU KYC profile (contact details, mobile verification); GDPR Art.6(1)(b) | Relationship + 5 yrs after closure, then anonymize | maskMobile -> +8801712***678 | Implemented |
| 17 | `alternate_mobile_no` | VARCHAR(20) | customers.alternate_mobile_no (proposed) | - | - | Restricted | BFIU KYC profile (contact details) | Relationship + 5 yrs after closure, then anonymize | maskMobile | NOT IMPLEMENTED |
| 18 | `address_line_1` | VARCHAR(200) | customers.address_line_1 (proposed) | API accepts addressLine1, then discards it | - | Restricted | FATF R.10 / BFIU KYC (address verification). Currently collected but NOT stored: compliance gap | Relationship + 5 yrs after closure, then anonymize | maskAddress -> ***, Dhaka | NOT IMPLEMENTED |
| 19 | `address_line_2` | VARCHAR(200) | customers.address_line_2 (proposed) | - | - | Restricted | FATF R.10 / BFIU KYC (address) | Relationship + 5 yrs after closure, then anonymize | maskAddress | NOT IMPLEMENTED |
| 20 | `city` | VARCHAR(100) | customers.city (proposed) | - | - | Confidential | FATF R.10 / BFIU KYC (address) | Relationship + 5 yrs after closure, then anonymize | None (kept in maskAddress output) | NOT IMPLEMENTED |
| 21 | `district` | VARCHAR(100) | customers.district (proposed) | - | - | Confidential | FATF R.10 / BFIU KYC (address) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 22 | `postal_code` | VARCHAR(20) | customers.postal_code (proposed) | - | - | Confidential | FATF R.10 / BFIU KYC (address) | Relationship + 5 yrs after closure, then anonymize | maskAddress | NOT IMPLEMENTED |
| 23 | `country` | CHAR(2) | customers.country (proposed) | - | - | Internal | FATF R.19 (country risk) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |

**C) Classification**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 24 | `customer_type` | VARCHAR(20) | customers.customer_type (proposed) | - | - | Internal | FATF R.10 (CDD differs for legal persons) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 25 | `customer_category` | VARCHAR(20) | customers.customer_category (proposed) | - | - | Internal | BFIU risk-based approach (customer risk categories) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 26 | `is_pep` | BOOLEAN | customers.is_pep (proposed) | None stored. Optional input on the fraud customer-created webhook; persisted only inside fraud_alerts.details when it triggers an escalation | Fraud | Restricted | FATF R.12 (PEP enhanced due diligence); BFIU master circular (PEP/IP) | Relationship + 5 yrs after closure, then anonymize | maskSensitiveFlag -> *** | NOT IMPLEMENTED |
| 27 | `is_staff` | BOOLEAN | customers.is_staff (proposed) | - | - | Confidential | None specific (insider-account control is internal policy); GDPR Art.6(1)(f) | Relationship + 5 yrs after closure, then anonymize | maskSensitiveFlag | NOT IMPLEMENTED |
| 28 | `is_minor` | BOOLEAN | customers.is_minor (proposed) | - | - | Confidential | Contract Act 1872 s.11 (capacity); GDPR Art.8 (child) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 29 | `guardian_customer_id` | UUID | customers.guardian_customer_id (proposed) | - | - | Confidential | Contract Act 1872 s.11 (guardian acts for a minor) | Relationship + 5 yrs after closure, then anonymize | None (opaque UUID) | NOT IMPLEMENTED |

**D) KYC / compliance**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 30 | `kyc_status` | VARCHAR(20) | customers.kyc_status | Customer (default pending; STAFF PATCH /kyc) | Customer, Ledger (transfer gate), events | Confidential | FATF R.10 (CDD completed before the relationship); BFIU KYC | Relationship + 5 yrs after closure, then anonymize | None (enum) | Implemented |
| 31 | `kyc_verified_at` | TIMESTAMPTZ | customers.kyc_verified_at (proposed) | - | - | Internal | FATF R.10/R.11 (record of when CDD was completed) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 32 | `kyc_verified_by` | UUID | customers.kyc_verified_by (proposed) | None: verifiedBy is accepted, copied into the kyc.verified event, then discarded | - | Confidential | FATF R.11 + GDPR Art.5(2) (who verified; accountability) | Relationship + 5 yrs after closure, then anonymize | None (opaque UUID) | NOT IMPLEMENTED |
| 33 | `aml_risk_rating` | VARCHAR(10) | customers.aml_risk_rating | Customer (default low; STAFF or Fraud via PATCH /aml-risk) | Customer, Fraud (rule 3 + Redis) | Restricted | FATF R.1/R.10 (risk-based approach); BFIU risk grading; FATF R.21 (no tipping-off: never disclose to the customer) | Relationship + 5 yrs after closure, then anonymize | maskSensitiveFlag -> *** (hide from CUSTOMER role) | Implemented |
| 34 | `sanctions_screened_at` | TIMESTAMPTZ | customers.sanctions_screened_at (proposed) | - | - | Internal | FATF R.6 (targeted financial sanctions); Anti-Terrorism Act 2009 [verify] | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 35 | `sanctions_match` | BOOLEAN | customers.sanctions_match (proposed) | - | - | Restricted | FATF R.6; GDPR Art.10 (offence-related data) [counsel to confirm] | Relationship + 5 yrs after closure, then anonymize | maskSensitiveFlag -> *** | NOT IMPLEMENTED |

**E) Relationship**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 36 | `home_branch_id` | UUID | customers.home_branch_id (proposed) | - | - | Internal | None (operational routing) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 37 | `relationship_manager_id` | UUID | customers.relationship_manager_id (proposed) | - | - | Confidential | None (operational); employee data under GDPR Art.6(1)(f) | Relationship + 5 yrs after closure, then anonymize | None (opaque UUID) | NOT IMPLEMENTED |

**F) Lifecycle**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 38 | `status` | VARCHAR(20) | customers.status | Customer (default active) | Customer | Internal | MLPA 2012 s.25(1): closure starts the 5-year retention clock | Relationship + 5 yrs after closure, then anonymize | None (enum) | Implemented |
| 39 | `onboarding_date` | DATE | customers.onboarding_date (proposed) | - | - | Internal | FATF R.11 (start of the business relationship) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 40 | `activated_at` | TIMESTAMPTZ | customers.activated_at (proposed) | - | - | Internal | None (operational) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 41 | `closed_at` | TIMESTAMPTZ | customers.closed_at (proposed) | - | - | Internal | MLPA 2012 s.25(1): trigger for the 5-year retention clock | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 42 | `closure_reason` | VARCHAR(100) | customers.closure_reason (proposed) | - | - | Confidential | None (operational); GDPR Art.5(1)(c) (use coded values, not free text) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |

**G) Audit**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 43 | `created_at` | TIMESTAMPTZ | customers.created_at | Customer | Customer | Internal | GDPR Art.5(2) accountability; Bangladesh Bank ICT Security Guideline (audit trail) [verify clause] | Relationship + 5 yrs after closure, then anonymize | None | Implemented |
| 44 | `created_by` | UUID | customers.created_by (proposed) | - | - | Internal | GDPR Art.5(2); Bangladesh Bank ICT Security Guideline (who changed what) [verify clause] | Relationship + 5 yrs after closure, then anonymize | None (opaque UUID) | NOT IMPLEMENTED |
| 45 | `updated_at` | TIMESTAMPTZ | customers.updated_at | Customer | Customer | Internal | GDPR Art.5(2) accountability | Relationship + 5 yrs after closure, then anonymize | None | Implemented |
| 46 | `updated_by` | UUID | customers.updated_by (proposed) | - | - | Internal | GDPR Art.5(2); Bangladesh Bank ICT Security Guideline [verify clause] | Relationship + 5 yrs after closure, then anonymize | None (opaque UUID) | NOT IMPLEMENTED |

**H) Documents**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 47 | `document_type` | VARCHAR(30) | customer_documents.document_type (proposed table) | - | - | Confidential | FATF R.10 (document used to verify identity) | Relationship + 5 yrs after closure, then anonymize | None (enum) | NOT IMPLEMENTED |
| 48 | `document_number` | VARCHAR(50) | customer_documents.document_number (proposed table) | - | - | Restricted | FATF R.10 (CDD); MLPA 2012 s.25(1); BFIU KYC/CDD master circular (copy of identity document) | Relationship + 5 yrs after closure, then anonymize | maskDocumentNumber -> ****-****-7890 | NOT IMPLEMENTED |
| 49 | `front_image_url` | VARCHAR(500) | customer_documents.front_image_url (proposed table) | - | - | Restricted | FATF R.10 (copy of the identity document). PCI DSS Req.3 applies only if the image shows a card PAN; otherwise None | Relationship + 5 yrs after closure, then anonymize | maskImageUrl -> https://host/*** | NOT IMPLEMENTED |
| 50 | `back_image_url` | VARCHAR(500) | customer_documents.back_image_url (proposed table) | - | - | Restricted | Same as front_image_url | Relationship + 5 yrs after closure, then anonymize | maskImageUrl | NOT IMPLEMENTED |
| 51 | `is_primary` | BOOLEAN | customer_documents.is_primary (proposed table) | - | - | Internal | None (operational) | Relationship + 5 yrs after closure, then anonymize | None | NOT IMPLEMENTED |
| 52 | `verification_status` | VARCHAR(20) | customer_documents.verification_status (proposed table) | - | - | Confidential | FATF R.10 (documents must be verified) | Relationship + 5 yrs after closure, then anonymize | None (enum) | NOT IMPLEMENTED |

**I) Consents**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 53 | `consent_type` | VARCHAR(40) | customer_consents.consent_type (proposed table) | - | - | Internal | GDPR Art.6(1)(a) and Art.7 (what was consented to) | Life of consent + 5 yrs | None (enum) | NOT IMPLEMENTED |
| 54 | `consent_given` | BOOLEAN | customer_consents.consent_given (proposed table) | - | - | Internal | GDPR Art.7(1) (demonstrate consent) | Life of consent + 5 yrs | None | NOT IMPLEMENTED |
| 55 | `given_at` | TIMESTAMPTZ | customer_consents.given_at (proposed table) | - | - | Internal | GDPR Art.7(1) (demonstrate when consent was given) | Life of consent + 5 yrs | None | NOT IMPLEMENTED |
| 56 | `revoked_at` | TIMESTAMPTZ | customer_consents.revoked_at (proposed table) | - | - | Internal | GDPR Art.7(3) (withdrawal must be as easy as giving) | Life of consent + 5 yrs | None | NOT IMPLEMENTED |
| 57 | `ip_address` | INET | customer_consents.ip_address (proposed table) | - | - | Restricted | GDPR Art.7(1) proof of consent; IP address is personal data (Recital 30) | Life of consent + 5 yrs, then null the column | maskIp -> 203.0.113.*** | NOT IMPLEMENTED |

**J) Derived / cached (Redis)**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 58 | `Redis kyc:{accountId}` | string "verified" | Redis | Ledger | Ledger | Confidential | None: cache of kyc_status; GDPR Art.5(1)(e) (keep short) | TTL 600 s (only "verified" is cached) | Key holds an account UUID only | Implemented |
| 59 | `Redis idem:{idempotencyKey}` | JSON TransferResponse | Redis | Ledger | Ledger | Confidential | None: replay protection; the legal record is transactions (FATF R.11) | TTL 3600 s | Key embeds a client-chosen key: keep it opaque (random UUID) | Implemented |
| 60 | `Redis lock:{accountId}` | string token | Redis | Ledger | Ledger | Internal | None (concurrency control) | TTL 5 s | None | Implemented |
| 61 | `Redis txn_count:{accountId}` | integer | Redis | Fraud | Fraud | Internal | FATF R.20 (monitoring for suspicious activity); velocity rule | TTL 60 s | None | Implemented |
| 62 | `Redis kyc:customer:{customerId}` | string "verified" | Redis | Ledger | Ledger | Confidential | None: cache of kyc_status | TTL 600 s | Key holds a customer UUID only | Implemented |
| 63 | `Redis cust_risk:{accountId}` | string (rating) | Redis | Fraud | Fraud | Restricted | None: cache of aml_risk_rating (FATF R.21: restrict access) | TTL 300 s | Value is Restricted: never log | Implemented |
| 64 | `Redis cust:acct:{accountId}` | JSON (id, number, name, mobile, email) | Redis | Notification | Notification | Restricted | None: cache of contact data; GDPR Art.5(1)(c)/(e). Holds raw PII: see gap S6 | TTL 600 s | Do not store full PII (store customerId only) | Implemented |
| 65 | `Redis cust:mobile:{mobileNo}` | JSON (as above) | Redis | Notification | Notification | Restricted | None: as above. The KEY contains the raw mobile number: see gap S6 | TTL 600 s | Hash the key (SHA-256) before use | Implemented |
| 66 | `Redis fraud_done:{transactionId}` | string "1" | Redis | Fraud | Fraud | Internal | None (idempotent event handling) | TTL 24 h | None | Implemented |
| 67 | `Redis notif_done:{transactionId}` | string "1" | Redis | Notification | Notification | Internal | None (idempotent event handling) | TTL 24 h | None | Implemented |
| 68 | `Redis welcome_done:{customerId}` | string "1" | Redis | Notification | Notification | Internal | None (idempotent event handling) | TTL 24 h | None | Implemented |
| 69 | `Redis revoked:jti:{jti}` | string "1" | Redis | Auth | Customer/Ledger app (all token-accepting services) | Internal | GDPR Art.32 (session termination) | TTL = remaining token life (max 15 min) | maskToken | Implemented |
| 70 | `Redis login_fail:{username}` | integer | Redis | Auth | Auth | Confidential | GDPR Art.32 (brute-force protection) | TTL 15 min | Key holds a username: maskName in logs | Implemented |

**K) Other implemented tables (accounts, transactions, ledger_entries, outbox, fraud_alerts, auth)**

| # | Field Name | Data Type | Source Table | Stored By | Read By | Sensitivity | Regulatory Basis | Retention Period | Masking Rule | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| 71 | `accounts.id` | UUID | accounts.id | Ledger | Ledger, Customer (join), Fraud/Notification (event payloads) | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None (opaque); log first 8 chars | Implemented |
| 72 | `accounts.customer_id` | UUID | accounts.customer_id | Ledger | Ledger, Customer (by-account lookup, AccessPolicy) | Internal | FATF R.11; MLPA 2012 s.25(1) (link customer to account) | Closure + 5 yrs, then archive/delete | None (opaque UUID) | Implemented |
| 73 | `accounts.account_number` | VARCHAR(30) | accounts.account_number | Ledger | Ledger | Confidential | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | maskAccountNumber -> ACC-******0004 | Implemented |
| 74 | `accounts.account_type` | VARCHAR(20) | accounts.account_type | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 75 | `accounts.balance_cents` | BIGINT | accounts.balance_cents | Ledger | Ledger (balance API, funds check) | Confidential | FATF R.11; MLPA 2012 s.25(1) (reconstruct balances) | Closure + 5 yrs, then archive/delete | None for the owner; never log amounts with names | Implemented |
| 76 | `accounts.status` | VARCHAR(20) | accounts.status | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 77 | `accounts.created_at` | TIMESTAMPTZ | accounts.created_at | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 78 | `accounts.updated_at` | TIMESTAMPTZ | accounts.updated_at | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 79 | `transactions.id` | UUID | transactions.id | Ledger | Ledger, Fraud, Notification (events) | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None (opaque) | Implemented |
| 80 | `transactions.idempotency_key` | VARCHAR(100) | transactions.idempotency_key | Ledger (value chosen by the client) | Ledger; Fraud/Notification receive it in the event but ignore it | Confidential | None: technical replay key. Free text could embed personal data: see gap S2 | Closure + 5 yrs, then archive/delete | maskToken | Implemented |
| 81 | `transactions.from_account` | UUID | transactions.from_account | Ledger | Ledger, Fraud, Notification | Internal | FATF R.11; MLPA 2012 s.25(1); FATF R.16 (originator information) | Closure + 5 yrs, then archive/delete | None (opaque) | Implemented |
| 82 | `transactions.to_account` | UUID | transactions.to_account | Ledger | Ledger, Fraud, Notification | Internal | FATF R.11; MLPA 2012 s.25(1); FATF R.16 (beneficiary information) | Closure + 5 yrs, then archive/delete | None (opaque) | Implemented |
| 83 | `transactions.amount_cents` | BIGINT | transactions.amount_cents | Ledger | Ledger, Fraud, Notification | Confidential | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None for the owner | Implemented |
| 84 | `transactions.status` | VARCHAR(20) | transactions.status | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 85 | `transactions.created_at` | TIMESTAMPTZ | transactions.created_at | Ledger | Ledger, Fraud, Notification | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 86 | `ledger_entries.id` | BIGSERIAL | ledger_entries.id | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete (append-only: see D5 legal override) | None | Implemented |
| 87 | `ledger_entries.transaction_id` | UUID | ledger_entries.transaction_id | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None (opaque) | Implemented |
| 88 | `ledger_entries.account_id` | UUID | ledger_entries.account_id | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None (opaque) | Implemented |
| 89 | `ledger_entries.direction` | VARCHAR(6) | ledger_entries.direction | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) (double-entry) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 90 | `ledger_entries.amount_cents` | BIGINT | ledger_entries.amount_cents | Ledger | Ledger | Confidential | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None for the owner | Implemented |
| 91 | `ledger_entries.created_at` | TIMESTAMPTZ | ledger_entries.created_at | Ledger | Ledger | Internal | FATF R.11; MLPA 2012 s.25(1) | Closure + 5 yrs, then archive/delete | None | Implemented |
| 92 | `outbox.id` | BIGSERIAL | outbox.id | Ledger | Ledger | Internal | None (delivery mechanism) | Delivered rows: 30 days (policy; not enforced today) | None | Implemented |
| 93 | `outbox.event_type` | VARCHAR(100) | outbox.event_type | Ledger | Ledger | Internal | None (delivery mechanism) | As above | None | Implemented |
| 94 | `outbox.payload` | JSONB | outbox.payload | Ledger | Ledger (relay to QStash) | Confidential | None: copy of the event; GDPR Art.5(1)(c)/(e). Kept forever today: see gap S3 | As above | Never log payloads | Implemented |
| 95 | `outbox.published` | BOOLEAN | outbox.published | Ledger | Ledger | Internal | None (delivery mechanism) | As above | None | Implemented |
| 96 | `outbox.created_at` | TIMESTAMPTZ | outbox.created_at | Ledger | Ledger | Internal | None (delivery mechanism) | As above | None | Implemented |
| 97 | `fraud_alerts.id` | UUID | fraud_alerts.id | Fraud | Fraud | Internal | FATF R.20/R.11 (suspicious activity records) ; BFIU STR/SAR guidance [verify] | 5 yrs from alert (legal hold overrides) | None | Implemented |
| 98 | `fraud_alerts.transaction_id` | UUID | fraud_alerts.transaction_id | Fraud | Fraud | Internal | As fraud_alerts.id | As above | None (opaque) | Implemented |
| 99 | `fraud_alerts.account_id` | UUID | fraud_alerts.account_id | Fraud | Fraud | Internal | As fraud_alerts.id | As above | None (opaque) | Implemented |
| 100 | `fraud_alerts.rule_fired` | VARCHAR(50) | fraud_alerts.rule_fired | Fraud | Fraud | Confidential | As fraud_alerts.id; FATF R.21 (never disclose to the customer) | As above | None; never show to CUSTOMER role | Implemented |
| 101 | `fraud_alerts.severity` | VARCHAR(20) | fraud_alerts.severity | Fraud | Fraud | Confidential | As above | As above | None | Implemented |
| 102 | `fraud_alerts.details` | JSONB | fraud_alerts.details | Fraud | Fraud | Restricted | As above. May contain is_pep / country_risk for onboarding alerts | As above | maskSensitiveFlag for flags; never log raw | Implemented |
| 103 | `fraud_alerts.created_at` | TIMESTAMPTZ | fraud_alerts.created_at | Fraud | Fraud | Internal | As fraud_alerts.id | As above | None | Implemented |
| 104 | `auth_users.id` | UUID | auth_users.id | Auth | Auth (token subject) | Internal | GDPR Art.32 (authentication) | Disable at closure; delete 90 days after closure | None (opaque) | Implemented |
| 105 | `auth_users.username` | VARCHAR(100) | auth_users.username | Auth | Auth; token claim "username" | Confidential | GDPR Art.6(1)(b) (account login) | As above | maskName | Implemented |
| 106 | `auth_users.password_hash` | VARCHAR(100) | auth_users.password_hash | Auth | Auth | Restricted | GDPR Art.32 (hashing of credentials: BCrypt) | As above | Never log or return | Implemented |
| 107 | `auth_users.role` | VARCHAR(20) | auth_users.role | Auth | Auth; token claim | Internal | GDPR Art.32 (access control) | As above | None | Implemented |
| 108 | `auth_users.customer_id` | UUID | auth_users.customer_id | Auth | Auth; token claim "cid" | Internal | GDPR Art.32 (ownership checks) | As above | None (opaque UUID) | Implemented |
| 109 | `auth_users.enabled` | BOOLEAN | auth_users.enabled | Auth | Auth | Internal | GDPR Art.32 | As above | None | Implemented |
| 110 | `auth_users.created_at` | TIMESTAMPTZ | auth_users.created_at | Auth | Auth | Internal | GDPR Art.5(2) | As above | None | Implemented |
| 111 | `refresh_tokens.id` | UUID | refresh_tokens.id | Auth | Auth | Internal | GDPR Art.32 | Expiry + 1 day | None | Implemented |
| 112 | `refresh_tokens.user_id` | UUID | refresh_tokens.user_id | Auth | Auth | Internal | GDPR Art.32 | Expiry + 1 day | None | Implemented |
| 113 | `refresh_tokens.token_hash` | CHAR(64) | refresh_tokens.token_hash | Auth | Auth | Restricted | GDPR Art.32 (only the SHA-256 is stored) | Expiry + 1 day | maskToken | Implemented |
| 114 | `refresh_tokens.expires_at` | TIMESTAMPTZ | refresh_tokens.expires_at | Auth | Auth | Internal | GDPR Art.32 | Expiry + 1 day | None | Implemented |
| 115 | `refresh_tokens.revoked` | BOOLEAN | refresh_tokens.revoked | Auth | Auth | Internal | GDPR Art.32 | Expiry + 1 day | None | Implemented |
| 116 | `refresh_tokens.created_at` | TIMESTAMPTZ | refresh_tokens.created_at | Auth | Auth | Internal | GDPR Art.32 | Expiry + 1 day | None | Implemented |

Notes on the table:
- `display_name` is a copy of first + last name (a minimisation finding, gap S1).
- `addressLine1` is validated by `POST /api/customers` and then **thrown away**: the brief's address fields are therefore listed as not implemented, and the collection-without-storage is a KYC gap (G1).
- `kyc_verified_by`: the API requires `verifiedBy` but only copies it into the `kyc.verified` event; it is not stored.
- Retention for rows with the "Relationship + 5 yrs after closure" rule assumes the closure date is recorded; today there is no `closed_at` column (gap G6).

---

## Deliverable 2: data flow map (10 most sensitive fields)

| Field | Writer service | Database table | Reader services | Cached where (Redis key + TTL) | Exposed in an API response DTO? |
|---|---|---|---|---|---|
| `id_number` | Customer (`POST /api/customers`, STAFF) | `customers.id_number` (UNIQUE) | Customer only (`existsByIdNumber` duplicate check) | Not cached | **No.** Accepted in `CreateCustomerRequest`; `CustomerResponse` omits it |
| `mobile_no` | Customer (`POST /api/customers`) | `customers.mobile_no` (UNIQUE) | Customer (duplicate check, by-mobile lookup); Notification (SMS stub, via by-account/by-mobile); Fraud and Notification also receive it in the `customer.created` event | Notification: `cust:acct:{accountId}` (JSON incl. mobile, 600 s) and `cust:mobile:{mobileNo}` (**the key itself contains the number**, 600 s) | **Yes, unmasked:** `CustomerResponse.mobileNo` on all 6 customer endpoints |
| `email` | Customer (`POST /api/customers`) | `customers.email` (UNIQUE, nullable) | Customer; Notification (sends mail; Redis cache); Fraud receives it in the `customer.created` event | Notification: `cust:acct:{accountId}` / `cust:mobile:{mobileNo}` (600 s) | **Yes, unmasked:** `CustomerResponse.email` on all 6 customer endpoints |
| `kyc_status` | Customer (default `pending`; STAFF `PATCH /kyc`) | `customers.kyc_status` | Customer; Ledger (transfer and account-opening gate); copied into `customer.created` and `kyc.verified` events | Ledger: `kyc:{accountId}` and `kyc:customer:{customerId}`, value `verified` only, 600 s | Yes: `CustomerResponse.kycStatus` (Confidential, not masked; acceptable for the owner and staff) |
| `aml_risk_rating` | Customer (default `low`); STAFF or Fraud (SYSTEM) via `PATCH /aml-risk` | `customers.aml_risk_rating` | Customer; Fraud (rule 3 via `/by-account`) | Fraud: `cust_risk:{accountId}` (rating, 300 s) | **Yes, unmasked, to every role including CUSTOMER:** `CustomerResponse.amlRiskRating`; also Fraud's `customer-created` webhook response `aml_risk_rating`. Tipping-off risk (FATF R.21) |
| `is_pep` | **None** (not stored). Optional input on Fraud's `customer-created` webhook; no publisher sends it today | Only inside `fraud_alerts.details` when an onboarding alert fires | Fraud | Not cached | No |
| `balance_cents` | Ledger (atomic `UPDATE` on transfer; opening deposit) | `accounts.balance_cents` | Ledger (balance API, insufficient-funds check) | Not cached | Yes: `BalanceResponse.balanceCents`, `AccountResponse.balanceCents`. Confidential; allowed because ownership is enforced (CUSTOMER: own account only; STAFF) |
| `idempotency_key` | Client chooses it; Ledger stores it | `transactions.idempotency_key` (UNIQUE); copied into the `transfer.completed` event and `outbox.payload` | Ledger; Fraud and Notification receive it in the event and ignore it | Ledger: `idem:{idempotencyKey}` (key contains the client value; value is the `TransferResponse`; 3600 s) | No (request only; not in any response DTO) |
| `front_image_url` | **Not implemented.** Proposed: Customer, document-upload endpoint (STAFF) | Proposed `customer_documents.front_image_url` | Proposed: Customer/STAFF review only | Must never be cached; if served, use a short-lived signed URL | Not today. When built: never in `CustomerResponse`; staff-only DTO, masked with `maskImageUrl` in logs |
| `ip_address` | **Not implemented.** Proposed: Customer, consent endpoint (reads `X-Forwarded-For` behind Render) | Proposed `customer_consents.ip_address` | Proposed: compliance/STAFF only | Not cached | Not today. When built: never returned to the customer-facing API; `maskIp` in logs |

---

## Deliverable 3: API exposure audit (PII leak audit)

Legend: ⚠ = a **Restricted** field is returned or exposed **unmasked**. `CustomerResponse` = `{id, customerNumber, displayName, mobileNo, email, kycStatus, amlRiskRating, status, createdAt}`.

| Service | Endpoint | Customer fields in the request | Customer fields in the response | Flag |
|---|---|---|---|---|
| Customer | `POST /api/customers` | firstName, lastName, dateOfBirth, mobileNo, email, idType, **idNumber**, addressLine1 (discarded) | `CustomerResponse` | ⚠ `mobileNo`, `email`, `amlRiskRating` unmasked. `idNumber` and `dateOfBirth` correctly omitted |
| Customer | `GET /api/customers/{id}` | none (path: customer id) | `CustomerResponse` | ⚠ `mobileNo`, `email`, `amlRiskRating` |
| Customer | `GET /api/customers/by-mobile/{mobileNo}` | **mobileNo in the URL path** | `CustomerResponse` | ⚠ response fields as above. ⚠ the mobile number is in the URL, so it lands in Render/proxy access logs and browser history. Send it in a POST body instead |
| Customer | `GET /api/customers/by-account/{accountId}` | none (path: account id) | `CustomerResponse` | ⚠ `mobileNo`, `email`, `amlRiskRating` |
| Customer | `PATCH /api/customers/{id}/kyc` | kycStatus, verifiedBy | `CustomerResponse` | ⚠ `mobileNo`, `email`, `amlRiskRating` |
| Customer | `PATCH /api/customers/{id}/aml-risk` | amlRiskRating | `CustomerResponse` | ⚠ `mobileNo`, `email`, `amlRiskRating` |
| Ledger | `POST /api/transfers` | fromAccount, toAccount, amountCents, idempotencyKey | transactionId, status, amountCents, createdAt | None (no customer PII) |
| Ledger | `GET /api/transfers/{id}` | none | transactionId, status, amountCents, createdAt | None |
| Ledger | `POST /api/accounts` | customerId, accountType, initialDepositCents | id, customerId, accountNumber, accountType, balanceCents, status, createdAt | None Restricted. `accountNumber` is Confidential (owner or STAFF only) |
| Ledger | `GET /api/accounts/{id}/balance` | none | accountId, balanceCents, currency | None |
| Ledger | `GET /api/accounts/{id}/entries` | none | id, transactionId, accountId, direction, amountCents, createdAt | None |
| Fraud | `POST /webhooks/transfer-completed` | event payload (D4): transaction_id, from_account, to_account, amount_cents, ... | transaction_id, flagged, rules | None (rules are Confidential; response goes only to QStash) |
| Fraud | `POST /webhooks/customer-created` | event payload: customer_id, customer_number, display_name, **email, mobile_no**, optional is_pep, country_risk | customer_id, **aml_risk_rating**, escalated | ⚠ `aml_risk_rating` returned to the caller (QStash stores delivery responses in its logs). Inbound PII is not needed by Fraud (D4) |
| Notification | `POST /webhooks/transfer-completed` | event payload | reference_id, email_sent, sms_logged, duplicate | None |
| Notification | `POST /webhooks/customer-created` | event payload incl. **email, mobile_no, display_name** | reference_id, email_sent, sms_logged, duplicate | None in the response. Inbound payload carries PII (needed for the welcome email today) |
| Auth | `POST /auth/login` | username, password | accessToken (JWT with claims `sub`, `roles`, `cid`, `username`), refreshToken | `username` is readable by anyone who holds the token; low risk, remove it from the claims if not needed |
| Auth | `POST /auth/users` | username, password, role, customerId | id, username, role, customerId | None Restricted |
| Auth | `POST /auth/refresh`, `/auth/logout`, `GET /auth/public-key` | refreshToken | tokens / empty / public key | None |
| All | `GET /actuator/health` | none | status | None |

**PII leak findings (Restricted data exposed unmasked)**

| # | Finding | Where | Severity | Recommended fix |
|---|---|---|---|---|
| F1 | `mobileNo` and `email` returned unmasked | `CustomerResponse` on 6 endpoints (CUSTOMER sees only their own; STAFF and SYSTEM see all) | Medium | Return masked values to STAFF by default; full values only to SYSTEM (Notification needs them) and to the data subject; add a staff "reveal" action that writes to `pii_access_log` |
| F2 | `amlRiskRating` returned to the CUSTOMER role | `CustomerResponse` | **High** (FATF R.21 tipping-off) | Remove from the CUSTOMER view; keep for STAFF and SYSTEM |
| F3 | Mobile number in the URL path | `GET /api/customers/by-mobile/{mobileNo}` | Medium | Replace with `POST /api/customers/search` and a JSON body |
| F4 | `aml_risk_rating` in the Fraud webhook response | `/webhooks/customer-created` | Low | Return only `{customer_id, escalated}` |
| F5 | PII in event payloads | `customer.created` (display_name, email, mobile_no) | Medium | See D4 |
| F6 | PII in Redis (name/mobile/email JSON, mobile inside a key) | Notification cache | Medium | Cache only the customer id; hash keys |
| F7 | Full email and mobile written to logs | Notification `EMAIL sent to ...`, `SMS to ...` | Medium | **Fixed in this change:** now masked with `MaskingUtil` |
| F8 | JWT carries `username` | Access token | Low | Drop the claim |

---

## Deliverable 4: event payload inventory

All events go to the QStash URL group `banking-events`, which fans **every** event out to **all four** webhook endpoints, so every consumer receives every payload whether it needs it or not. Wire format is snake_case. QStash keeps delivery logs and messages that exhausted their retries stay in the DLQ until deleted.

| Event | Published by | Exact current payload fields | PII / sensitive fields |
|---|---|---|---|
| `customer.created` | Customer (direct publish, inside the create transaction) | `event, customer_id, customer_number, kyc_status, occurred_at, display_name, email, mobile_no` | **display_name, email, mobile_no** (Restricted/Confidential) |
| `kyc.verified` | Customer (direct publish) | `event, customer_id, customer_number, kyc_status, occurred_at, verified_by` | `verified_by` (staff-supplied free text); `customer_number` |
| `transfer.completed` | Ledger (via outbox) | `event, transaction_id, from_account, to_account, amount_cents, created_at, idempotency_key, status, debit_entry_id, credit_entry_id` | No direct PII. Pseudonymous account ids and the amount (financial data). `idempotency_key` is client free text |
| `account.created` | Ledger (via outbox) | `event, account_id, customer_id, account_number, account_type, initial_deposit_cents, created_at` | `customer_id` + `account_number` link a person to an account |

**Current vs recommended (minimal) payloads**

| Event | Current | Recommended minimal | Why it is enough |
|---|---|---|---|
| `customer.created` | `{"event","customer_id","customer_number","kyc_status","occurred_at","display_name","email","mobile_no"}` | `{"event":"customer.created","event_id":"<uuid>","customer_id":"<uuid>","occurred_at":"<ts>"}` | Fraud only needs `customer_id`. Notification fetches name/email with `GET /api/customers/{id}` (SYSTEM key) and caches the customer id, not the PII. Costs one extra call per welcome email |
| `kyc.verified` | `{"event","customer_id","customer_number","kyc_status","occurred_at","verified_by"}` | `{"event":"kyc.verified","event_id":"<uuid>","customer_id":"<uuid>","occurred_at":"<ts>"}` | No consumer acts on it today (all endpoints ignore it). Drop `verified_by` (store it in the database, `kyc_verified_by`) or stop publishing until a consumer exists |
| `transfer.completed` | `{"event","transaction_id","from_account","to_account","amount_cents","created_at","idempotency_key","status","debit_entry_id","credit_entry_id"}` | `{"event":"transfer.completed","event_id":"<uuid>","transaction_id":"<uuid>","from_account":"<uuid>","to_account":"<uuid>","amount_cents":10000,"occurred_at":"<ts>"}` | Fraud needs transaction, from account, amount. Notification needs the same plus `to_account` for the message. `idempotency_key`, `status` and ledger entry ids are internal |
| `account.created` | `{"event","account_id","customer_id","account_number","account_type","initial_deposit_cents","created_at"}` | `{"event":"account.created","event_id":"<uuid>","account_id":"<uuid>","occurred_at":"<ts>"}` | No consumer uses it today (all endpoints ignore it): publish nothing until one exists, then send ids only |

Also: put an `event_id` in every payload (consumers can then dedupe on it), and have Fraud and Notification reject fields they do not use at the schema level.

---

## Deliverable 5: retention and deletion schedule

Retention clock for AML records: **5 years after account closure** (MLPA 2012 s.25(1); FATF R.11). GDPR Art.5(1)(e) requires deleting or anonymizing once the purpose and the legal duty have ended; Art.17(3)(b) lets the bank refuse erasure while a legal obligation requires the data.

| Data category | Table / store | Retention period | Legal basis | Deletion method | Trigger |
|---|---|---|---|---|---|
| Customer PII (names, DOB, contact, address, ID number) | `customers` | Relationship + 5 yrs after closure | MLPA 2012 s.25(1); FATF R.10/R.11; GDPR Art.6(1)(c) | **Anonymize** (replace with irreversible placeholders; keep `id`, `customer_number`, status, timestamps so ledger links survive) | Account closure + 5 yrs. An erasure request before then: restrict processing (GDPR Art.18), refuse deletion under Art.17(3)(b), delete at expiry |
| KYC documents and images | `customer_documents` + private file storage (proposed) | Closure + 5 yrs | FATF R.10; MLPA 2012 s.25(1) | **Hard delete** the files and clear the numbers; keep an anonymized verification record | Closure + 5 yrs |
| Transaction records | `transactions`, `accounts` | Closure + 5 yrs | MLPA 2012 s.25(1); FATF R.11 | **Archive** to cold storage, then hard delete; null `idempotency_key` at archive time | Closure + 5 yrs |
| Ledger entries | `ledger_entries` | Closure + 5 yrs | MLPA 2012 s.25(1); FATF R.11; accounting integrity | **Cannot be deleted or altered** by the application: the table is append-only (database rules turn `UPDATE`/`DELETE` into no-ops). **Legal override:** GDPR Art.17(3)(b) (legal obligation) and Art.17(3)(e) (legal claims). The table holds only `account_id`, amount and direction, no direct PII; once `customers` is anonymized the entries are no longer attributable to a person. Physical removal after expiry only by a DBA with written approval, after archiving | Closure + 5 yrs |
| Fraud alerts | `fraud_alerts` | 5 yrs from the alert; longer while a `legal_hold` is set | FATF R.20/R.11; BFIU STR/SAR guidance [verify] | **Archive**, then hard delete. Never disclose to the customer (FATF R.21) | Time-based; extended by investigation |
| Consent records | `customer_consents` (proposed) | Life of the consent + 5 yrs | GDPR Art.7(1) (prove consent); Art.17(3)(e) (legal claims) | Keep, **null `ip_address`** after the period, then delete the row | Time-based after revocation |
| Outbox payloads | `outbox` | Delivered rows: 30 days (**not enforced; kept forever today**) | GDPR Art.5(1)(c),(e) | **Hard delete** rows where `published = true` and older than 30 days | Time-based |
| Auth identities | `auth_users`, `refresh_tokens` | `auth_users`: disable at closure, delete 90 days later. `refresh_tokens`: expiry + 1 day | GDPR Art.5(1)(e), Art.32 | Hard delete | Account closure / time-based |
| Redis cache keys | Redis | 5 s to 24 h by key (see D1 section J); `revoked:jti` = token life | GDPR Art.5(1)(e) | **Expires by TTL.** On erasure/closure also `DEL cust:*`, `kyc:*`, `cust_risk:*` for that customer | TTL; closure; consent revocation |
| QStash event logs and DLQ | Upstash | Provider-defined log retention (check the Upstash console; do not assume); DLQ until deleted | GDPR Art.5(1)(e); Art.28 (processor) | Delete DLQ entries after triage (at most 30 days); minimise payloads (D4) so logs hold no PII | Time-based |
| Application logs | Render logs | Provider-defined (short on the free plan) | GDPR Art.5(1)(c) | Do not log PII: use `MaskingUtil` | n/a |

**How an erasure request is handled** (summary; the customer-facing version is in Deliverable 8): verify identity -> check AML hold -> if within the 5-year clock, restrict processing and tell the customer what is retained and until when -> at expiry (or immediately if no hold applies) anonymize `customers`, delete documents, delete `auth_users`, flush Redis keys -> record the action in `pii_access_log` (proposed).

---

## Deliverable 6: masking rules

`MaskingUtil` lives in the shared starter, so every service already depends on it: `security-starter/src/main/java/com/Bank_website/security/MaskingUtil.java`. It is unit-tested (`MaskingUtilTest`) and already applied in Notification's logs.

| Field | Function | Example |
|---|---|---|
| mobile_no, alternate_mobile_no | `maskMobile` | `+8801712345678` -> `+8801712***678` |
| id_number, document_number | `maskIdNumber`, `maskDocumentNumber` | `1234567890` -> `****-****-7890` |
| email | `maskEmail` | `rahim@example.com` -> `r***@example.com` |
| date_of_birth | `maskDateOfBirth` | `1992-04-10` -> `1992-**-**` |
| names | `maskName` | `Rahim` -> `R***` |
| address lines, postal_code | `maskAddress` | `House 1, Road 2, Dhaka` -> `***, Dhaka` |
| account_number, customer_number | `maskAccountNumber` | `ACC-0000000004` -> `ACC-******0004` |
| front_image_url, back_image_url | `maskImageUrl` | `https://files.example.com/kyc/a.jpg?sig=x` -> `https://files.example.com/***` |
| ip_address | `maskIp` | `203.0.113.45` -> `203.0.113.***` |
| is_pep, sanctions_match, aml_risk_rating | `maskSensitiveFlag` | `true` -> `***` |
| refresh tokens, jti, idempotency keys | `maskToken` | `abcdefghijkl` -> `abcd...kl` |

```java
package com.Bank_website.security;

import java.net.URI;

/**
 * Masking for Restricted/Confidential personal data. Apply before writing to a log or returning a value
 * in an API response that does not need the raw value. Every method is null-safe (null in, null out) and
 * never throws. Rules are documented in docs/DATA_GOVERNANCE.md (Deliverable 6).
 */
public final class MaskingUtil {

    private static final String STARS = "***";

    private MaskingUtil() {
    }

    /** +8801712345678 -> +8801712***678 ; 01712345678 -> 01712***678 */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return mobile;
        }
        String m = mobile.replaceAll("[\\s-]", "");
        int head = m.startsWith("+880") ? 8 : m.startsWith("01") ? 5 : 3;
        if (m.length() <= head + 3) {
            return STARS;
        }
        return m.substring(0, head) + STARS + m.substring(m.length() - 3);
    }

    /** 1234567890 -> ****-****-7890 (national ID, passport, driving licence, birth certificate number) */
    public static String maskIdNumber(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            return idNumber;
        }
        String s = idNumber.replaceAll("[\\s-]", "");
        return s.length() <= 4 ? "****" : "****-****-" + s.substring(s.length() - 4);
    }

    /** Same rule as the ID number. */
    public static String maskDocumentNumber(String documentNumber) {
        return maskIdNumber(documentNumber);
    }

    /** rahim@example.com -> r***@example.com */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at < 1) {
            return STARS;
        }
        return email.charAt(0) + STARS + email.substring(at);
    }

    /** 1992-04-10 -> 1992-**-** (keeps the year so age checks still work) */
    public static String maskDateOfBirth(String isoDate) {
        if (isoDate == null || isoDate.length() < 4 || !isoDate.substring(0, 4).chars().allMatch(Character::isDigit)) {
            return isoDate == null ? null : STARS;
        }
        return isoDate.substring(0, 4) + "-**-**";
    }

    /** Rahim -> R*** */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return name.trim().charAt(0) + STARS;
    }

    /** "House 1, Road 2, Dhaka" -> "***, Dhaka" (only the last component, usually the city, survives) */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return address;
        }
        int comma = address.lastIndexOf(',');
        return comma < 0 ? STARS : STARS + ", " + address.substring(comma + 1).trim();
    }

    /** ACC-0000000004 -> ACC-******0004 */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber == null ? null : STARS;
        }
        int dash = accountNumber.indexOf('-');
        String prefix = dash > 0 ? accountNumber.substring(0, dash + 1) : "";
        String body = accountNumber.substring(prefix.length());
        if (body.length() <= 4) {
            return prefix + STARS;
        }
        return prefix + "*".repeat(body.length() - 4) + body.substring(body.length() - 4);
    }

    /** https://files.example.com/kyc/abc/front.jpg?sig=secret -> https://files.example.com/*** (drops path and signed query) */
    public static String maskImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return STARS;
            }
            return uri.getScheme() + "://" + uri.getHost() + "/" + STARS;
        } catch (IllegalArgumentException e) {
            return STARS;
        }
    }

    /** 203.0.113.45 -> 203.0.113.*** ; 2001:db8:85a3::8a2e -> 2001:db8:**** */
    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        if (ip.contains(":")) {
            String[] groups = ip.split(":");
            return groups.length < 2 ? STARS : groups[0] + ":" + groups[1] + ":****";
        }
        int lastDot = ip.lastIndexOf('.');
        return lastDot < 0 ? STARS : ip.substring(0, lastDot + 1) + STARS;
    }

    /** is_pep, sanctions_match, aml_risk_rating: never log or show the value. */
    public static String maskSensitiveFlag(Object value) {
        return value == null ? null : STARS;
    }

    /** Refresh tokens, JWT ids, API keys: abcdefghijkl -> abcd...kl */
    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return token;
        }
        return token.length() <= 8 ? STARS : token.substring(0, 4) + "..." + token.substring(token.length() - 2);
    }
}
```

---

## Deliverable 7: gap analysis

**Statements are also in `db/proposed/004_governance_gaps.sql` (not applied automatically; every new column is nullable or defaulted so `ddl-auto=validate` keeps passing).**

### 7a. Stored today with no (or weak) documented basis

| # | Field / store | Problem | Regulation | Fix |
|---|---|---|---|---|
| S1 | `customers.display_name` | Duplicates first + last name | GDPR Art.5(1)(c) minimisation | `ALTER TABLE customers ALTER COLUMN display_name DROP NOT NULL;` then stop writing it and compute it in the API |
| S2 | `transactions.idempotency_key` | Client free text may contain personal data | GDPR Art.5(1)(c) | `ALTER TABLE transactions ADD CONSTRAINT chk_idem_key_format CHECK (idempotency_key ~ '^[A-Za-z0-9_.:-]{8,100}$') NOT VALID;` |
| S3 | `outbox.payload` | Every event copy kept forever | GDPR Art.5(1)(e) | `ALTER TABLE outbox ADD COLUMN published_at TIMESTAMPTZ;` plus a daily purge of delivered rows older than 30 days |
| S4 | `fraud_alerts.details` | Free JSON may hold `is_pep`, `country_risk` (Restricted) with no retention or hold flag | FATF R.11/R.21 | `ALTER TABLE fraud_alerts ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT false, ADD COLUMN reviewed_at TIMESTAMPTZ;` |
| S5 | `auth_users` | No way to find dormant logins to delete | GDPR Art.5(1)(e) | `ALTER TABLE auth_users ADD COLUMN last_login_at TIMESTAMPTZ;` |
| S6 | Redis `cust:acct:*`, `cust:mobile:*` | Raw name/mobile/email cached; mobile number inside a key | GDPR Art.5(1)(c), Art.32 | Code change, no SQL: cache the customer id only and hash keys |
| S7 | Whole system | **No retention or deletion job exists** | GDPR Art.5(1)(e); MLPA 2012 s.25(1) | Needs `closed_at` / `retention_until` (G6) and a scheduled job |
| S8 | `customers.kyc_status` copied into events | Not a PII problem; listed because `customer_number` and `kyc_status` travel in events no consumer uses | GDPR Art.5(1)(c) | See D4 |

### 7b. Should exist for compliance but does not

| # | Missing | Regulation | Exact statement |
|---|---|---|---|
| G1 | Customer address (collected, then discarded) | FATF R.10; BFIU KYC (address verification) | `ALTER TABLE customers ADD COLUMN address_line_1 VARCHAR(200), ADD COLUMN address_line_2 VARCHAR(200), ADD COLUMN city VARCHAR(100), ADD COLUMN district VARCHAR(100), ADD COLUMN postal_code VARCHAR(20), ADD COLUMN country CHAR(2) DEFAULT 'BD';` |
| G2 | Who verified KYC and when | FATF R.10/R.11; GDPR Art.5(2) | `ALTER TABLE customers ADD COLUMN kyc_verified_at TIMESTAMPTZ, ADD COLUMN kyc_verified_by UUID;` |
| G3 | Created-by / updated-by audit trail | GDPR Art.5(2); Bangladesh Bank ICT Security Guideline [verify clause] | `ALTER TABLE customers ADD COLUMN created_by UUID, ADD COLUMN updated_by UUID;` |
| G4 | PEP flag, sanctions screening result and time | FATF R.12 (PEPs), R.6 (sanctions) | `ALTER TABLE customers ADD COLUMN is_pep BOOLEAN NOT NULL DEFAULT false, ADD COLUMN sanctions_screened_at TIMESTAMPTZ, ADD COLUMN sanctions_match BOOLEAN;` |
| G5 | ID validity dates, **KYC review due date and document-expiry alert timestamp** | FATF R.10(d) (ongoing due diligence); BFIU periodic KYC review | `ALTER TABLE customers ADD COLUMN id_issue_date DATE, ADD COLUMN id_expiry_date DATE, ADD COLUMN id_issuing_authority VARCHAR(100), ADD COLUMN kyc_review_due_at TIMESTAMPTZ, ADD COLUMN kyc_expiry_alert_sent_at TIMESTAMPTZ;` |
| G6 | Lifecycle and retention clock (`closed_at`, `retention_until`, erasure request and anonymization timestamps) | MLPA 2012 s.25(1); GDPR Art.5(1)(e), Art.17 | `ALTER TABLE customers ADD COLUMN onboarding_date DATE, ADD COLUMN activated_at TIMESTAMPTZ, ADD COLUMN closed_at TIMESTAMPTZ, ADD COLUMN closure_reason VARCHAR(100), ADD COLUMN retention_until DATE, ADD COLUMN deletion_requested_at TIMESTAMPTZ, ADD COLUMN anonymized_at TIMESTAMPTZ;` |
| G7 | **Data-processing purpose code** | GDPR Art.5(1)(b), Art.30(1)(b) | `ALTER TABLE customers ADD COLUMN processing_purpose_code VARCHAR(30) DEFAULT 'ACCOUNT_SERVICING';` |
| G8 | Encrypted ID number with a searchable hash | GDPR Art.32; Bangladesh Bank ICT Security Guideline [verify clause] | `ALTER TABLE customers ADD COLUMN id_number_hash CHAR(64);` plus a unique index on it; the application then stores `id_number` encrypted |
| G9 | Outbox delivery time | GDPR Art.5(1)(e) | `ALTER TABLE outbox ADD COLUMN published_at TIMESTAMPTZ;` (same as S3) |
| G10 | Consent records with **consent version number**, purpose, IP, withdrawal | GDPR Art.7(1), Art.7(3) | `CREATE TABLE customer_consents (...)` with `consent_version`, `purpose_code`, `ip_address INET`, `revoked_at` (full DDL in the proposed file) |
| G11 | KYC document records | FATF R.10 | `CREATE TABLE customer_documents (...)` (full DDL in the proposed file) |
| G12 | Log of who viewed personal data | GDPR Art.5(1)(f), Art.32; Bangladesh Bank ICT Security Guideline [verify clause] | `CREATE TABLE pii_access_log (...)` (full DDL in the proposed file) |
| G13 | Staff flag, minor/guardian, customer type/category, branch, relationship manager | Contract Act 1872 s.11; FATF R.10; BFIU risk-based approach | Add when the business need exists: `ALTER TABLE customers ADD COLUMN is_staff BOOLEAN DEFAULT false, ADD COLUMN is_minor BOOLEAN DEFAULT false, ADD COLUMN guardian_customer_id UUID REFERENCES customers(id), ADD COLUMN customer_type VARCHAR(20), ADD COLUMN customer_category VARCHAR(20), ADD COLUMN home_branch_id UUID, ADD COLUMN relationship_manager_id UUID;` (not in the proposed file: decide first whether the bank needs them) |

---

## Deliverable 8: one-page data governance summary (for the compliance officer)

**What customer data we hold.** Today the system holds a customer's name, date of birth, mobile number, email address, type and number of their identity document, whether their identity has been verified, an internal anti-money-laundering risk rating, and the dates the record was created and changed. For each bank account it holds the balance and every movement of money. For fraud monitoring it keeps a record of any transaction that tripped a rule. For online login it keeps a username, a scrambled (hashed) password and login sessions. Short-lived copies of some of this sit in a cache (a few seconds to 24 hours). The system also *asks for* a home address but does not yet keep it; that is a compliance gap we should close. Document scans, consent records, political-exposure and sanctions-screening results are planned but not built.

**Why we hold it.** We are legally required to identify customers, check them against money-laundering rules and keep transaction records. We also need contact details to tell customers about their transactions, and security data to protect their accounts.

**Who can see it.** Customers see only their own record and accounts. Bank staff can see any customer. Internal systems (fraud checks, notifications) see only what they need to do their job, through a separate service credential. Today, mobile numbers and emails are shown in full to everyone who is allowed to see a record, and the internal risk rating is shown to the customer as well; both should change (findings F1 and F2). The risk rating must never be shown to the customer.

**How long we keep it.** Customer identity, contact and account records are kept for as long as the customer is with us and for five years after the account is closed, because anti-money-laundering law requires it. Transaction history follows the same period. Fraud records are kept five years, longer if an investigation is open. Login sessions and caches are kept for minutes or days. After the period ends, personal details are replaced with anonymous placeholders and documents are destroyed. **Today no automatic clean-up exists; it must be built before launch.**

**How a customer can ask us to delete their data.** The customer asks in writing or through their branch. We confirm who they are, then check whether the law still requires us to keep their records. If they closed their account more than five years ago, we anonymize their personal details, delete their documents and login, and confirm to them in writing. If the five years have not passed, we cannot delete the identity and transaction records; we stop using them for anything except legal duties, tell the customer exactly what we keep and until when, and delete everything else straight away (for example marketing consent, optional contact details and their login once the account is closed).

**What cannot be deleted, and why.** Transaction and ledger history cannot be changed or erased during the five-year period: the law requires complete records, and the accounting book is deliberately write-once. Fraud and suspicious-activity records are also kept, and by law we may not tell the customer about them. After five years the ledger no longer identifies a person, because the customer record it points to has been anonymized.

**Top actions for management:** (1) hide the risk rating from customers and mask contact details by default; (2) store the address we already collect; (3) record who verified each customer and when; (4) build the retention and deletion job; (5) stop putting personal details into messages sent to other systems; (6) have counsel confirm the Bangladesh circular numbers cited in this document.
