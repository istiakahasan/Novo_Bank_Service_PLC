-- Seed data with FIXED UUIDs. Run AFTER schema.sql. Safe to re-run.
INSERT INTO customers (id, customer_number, first_name, last_name, display_name, date_of_birth, mobile_no, email, id_type, id_number, kyc_status) VALUES
 ('11111111-1111-4111-8111-111111111111','CUST000001','Alice','Smith','Alice Smith','1990-01-15','+8801700000001','alice@example.com','NID','NID-0000000001','verified'),
 ('22222222-2222-4222-8222-222222222222','CUST000002','Bob','Jones','Bob Jones','1988-06-20','+8801700000002','bob@example.com','NID','NID-0000000002','verified'),
 ('33333333-3333-4333-8333-333333333333','CUST000003','Charlie','Brown','Charlie Brown','1995-11-02','+8801700000003','charlie@example.com','NID','NID-0000000003','verified')
ON CONFLICT (id) DO NOTHING;

INSERT INTO accounts (id, customer_id, account_number, account_type, balance_cents) VALUES
 ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa','11111111-1111-4111-8111-111111111111','NOVO0000000001','savings',100000),
 ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb','22222222-2222-4222-8222-222222222222','NOVO0000000002','savings',50000),
 ('cccccccc-cccc-4ccc-8ccc-cccccccccccc','33333333-3333-4333-8333-333333333333','NOVO0000000003','savings',20000)
ON CONFLICT (id) DO NOTHING;

SELECT a.id AS account_id, c.display_name, a.account_number, a.balance_cents/100.0 AS balance_usd
FROM accounts a JOIN customers c ON c.id = a.customer_id ORDER BY a.account_number;
