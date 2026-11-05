// Interfaces mirror the backend DTO records exactly (JSON property names are the record component names).

export type Role = 'CUSTOMER' | 'STAFF';

export type KycStatus = 'pending' | 'verified' | 'rejected';
export type TicketCategory = 'TRANSACTION_DISPUTE' | 'ACCOUNT_LOCK' | 'KYC_VERIFICATION' | 'GENERAL_INQUIRY';
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'PENDING_CUSTOMER' | 'RESOLVED' | 'CLOSED';
export type SenderType = 'CUSTOMER' | 'AGENT' | 'SYSTEM';

/** Customer Service: CustomerResponse (idNumber is never returned). */
export interface CustomerResponse {
  id: string;
  customerNumber: string;
  displayName: string;
  mobileNo: string;
  email: string | null;
  kycStatus: KycStatus;
  amlRiskRating: 'low' | 'medium' | 'high';
  status: string;
  createdAt: string;
}

export interface KycUpdateRequest {
  kycStatus: 'verified' | 'rejected';
  verifiedBy: string;
}

/** Customer Service: GET /api/customers?kycStatus= (see docs/FRONTEND.md for the backend addition). */
export interface CustomerPage {
  content: CustomerResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Ledger Service */
export interface TransferRequest {
  fromAccount: string;
  toAccount: string;
  amountCents: number;
  idempotencyKey: string;
}

export interface TransferResponse {
  transactionId: string;
  status: string;
  amountCents: number;
  createdAt: string;
  // Not returned by the current backend DTO; rendered when present.
  fromAccount?: string;
  toAccount?: string;
}

export interface BalanceResponse {
  accountId: string;
  balanceCents: number;
  currency: string;
}

export interface LedgerEntryResponse {
  id: number;
  transactionId: string;
  accountId: string;
  direction: 'debit' | 'credit';
  amountCents: number;
  createdAt: string;
}

export interface CreateAccountRequest {
  customerId: string;
  accountType: 'savings' | 'checking';
  initialDepositCents: number;
}

export interface AccountResponse {
  id: string;
  customerId: string;
  accountNumber: string;
  accountType: string;
  balanceCents: number;
  status: string;
  createdAt: string;
}

/** Support Service */
export interface TicketMessageResponse {
  messageId: string;
  ticketId: string;
  senderId: string;
  senderType: SenderType;
  messageBody: string;
  isInternalNote: boolean;
  createdAt: string;
}

export interface TicketResponse {
  ticketId: string;
  customerId: string;
  assignedAgentId: string | null;
  category: TicketCategory;
  priority: TicketPriority;
  status: TicketStatus;
  subject: string;
  relatedTransactionId: string | null;
  createdAt: string;
  resolvedAt: string | null;
  messages?: TicketMessageResponse[];
}

export interface TicketListResponse {
  content: TicketResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateTicketRequest {
  category: TicketCategory;
  priority?: TicketPriority;
  subject: string;
  relatedTransactionId?: string;
}

export interface UpdateTicketRequest {
  status?: TicketStatus;
  assignedAgentId?: string;
  priority?: TicketPriority;
}

export interface CreateMessageRequest {
  messageBody: string;
  isInternalNote: boolean;
}

export interface TicketListParams {
  status?: TicketStatus | '';
  priority?: TicketPriority | '';
  assignedAgentId?: string;
  page?: number;
  size?: number;
}

/** What GET /api/session returns (derived from the signed session cookie, never from localStorage). */
export interface SessionInfo {
  role: Role;
  customerId: string | null;
  agentId: string;
  accountId: string | null;
}

export interface ApiErrorBody {
  status?: number;
  message?: string;
  timestamp?: string;
}
