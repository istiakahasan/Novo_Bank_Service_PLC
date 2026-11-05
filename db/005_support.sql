-- Support Service tables. Run in the Supabase SQL Editor (after schema.sql). Safe to re-run.
CREATE TABLE IF NOT EXISTS support_tickets (
  ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id UUID NOT NULL,
  assigned_agent_id UUID,
  category VARCHAR(30) NOT NULL CHECK (category IN ('TRANSACTION_DISPUTE','ACCOUNT_LOCK','KYC_VERIFICATION','GENERAL_INQUIRY')),
  priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','PENDING_CUSTOMER','RESOLVED','CLOSED')),
  subject VARCHAR(200) NOT NULL,
  related_transaction_id UUID,
  created_at TIMESTAMPTZ DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_tickets_customer ON support_tickets(customer_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON support_tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_agent ON support_tickets(assigned_agent_id);

CREATE TABLE IF NOT EXISTS ticket_messages (
  message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL REFERENCES support_tickets(ticket_id) ON DELETE CASCADE,
  sender_id UUID NOT NULL,
  sender_type VARCHAR(10) NOT NULL CHECK (sender_type IN ('CUSTOMER','AGENT','SYSTEM')),
  message_body TEXT NOT NULL,
  is_internal_note BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_ticket ON ticket_messages(ticket_id, created_at);

-- append-only: UPDATE and DELETE become no-ops
CREATE OR REPLACE RULE no_update_messages AS ON UPDATE TO ticket_messages DO INSTEAD NOTHING;
CREATE OR REPLACE RULE no_delete_messages AS ON DELETE TO ticket_messages DO INSTEAD NOTHING;

-- same convention as the other tables: block Supabase's public REST API, the backend uses a direct connection
ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE ticket_messages ENABLE ROW LEVEL SECURITY;
