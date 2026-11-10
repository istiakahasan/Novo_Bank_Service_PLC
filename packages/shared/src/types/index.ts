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

/** Customer Service: GET /api/customers?kycStatus= */
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

/** What GET /api/session returns */
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

/** Loan Service (:3009) */
export interface LoanProduct {
  productId: string;
  productCode: string;
  productName: string;
  minAmountCents: number;
  maxAmountCents: number;
  minTenureMonths: number;
  maxTenureMonths: number;
  baseInterestRateBps: number;
  isActive: boolean;
  createdAt: string;
}

export interface RepaymentSchedule {
  repaymentId: string;
  applicationId: string;
  dueDate: string;
  principalDueCents: number;
  interestDueCents: number;
  totalDueCents: number;
  paidAt: string | null;
  paymentTransactionId: string | null;
  status: 'PENDING' | 'PAID' | 'OVERDUE' | string;
  createdAt: string;
}

export type LoanApplicationStatus =
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'DISBURSED'
  | 'CLOSED'
  | string;

export interface LoanApplication {
  applicationId: string;
  customerId: string;
  productCode: string;
  productName: string;
  requestedAmountCents: number;
  requestedTenureMonths: number;
  status: LoanApplicationStatus;
  approvedAmountCents: number | null;
  approvedInterestRateBps: number | null;
  approvedTenureMonths: number | null;
  rejectionReason: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  disbursedAt: string | null;
  disbursementTransactionId: string | null;
  createdAt: string;
  repayments?: RepaymentSchedule[];
}

export interface CreateLoanApplicationRequest {
  productCode: string;
  requestedAmountCents: number;
  requestedTenureMonths: number;
}

export interface ReviewLoanRequest {
  action: 'APPROVE' | 'REJECT';
  approvedAmountCents?: number;
  approvedInterestRateBps?: number;
  approvedTenureMonths?: number;
  rejectionReason?: string;
}

export type CollateralType = 'PROPERTY' | 'VEHICLE' | 'FIXED_DEPOSIT' | 'SECURITIES' | 'OTHER' | string;
export type CollateralStatus = 'PLEDGED' | 'RELEASED' | 'SEIZED' | string;

export interface Collateral {
  collateralId: string;
  applicationId: string;
  collateralType: CollateralType;
  description: string;
  estimatedValueCents: number;
  valuationDate: string;
  status: CollateralStatus;
  createdAt: string;
}

export interface AddCollateralRequest {
  collateralType: CollateralType;
  description: string;
  estimatedValueCents: number;
  valuationDate: string;
}

export interface OverdueRepayment {
  repaymentId: string;
  applicationId: string;
  customerId: string;
  dueDate: string;
  daysOverdue: number;
  totalDueCents: number;
  lastActionType: string | null;
}

export type CollectionActionType =
  | 'REMINDER_SENT'
  | 'PHONE_CALL'
  | 'DEMAND_LETTER'
  | 'LEGAL_NOTICE'
  | 'WRITE_OFF_PROPOSED'
  | 'WRITE_OFF_APPROVED'
  | string;

export interface CollectionAction {
  actionId: string;
  applicationId: string;
  repaymentId: string;
  actionType: CollectionActionType;
  performedBy: string;
  notes: string;
  createdAt: string;
}

export interface CollectionActionRequest {
  actionType: CollectionActionType;
  notes: string;
}

/** Treasury Service (:3010) */
export interface TreasuryLiquidity {
  totalDepositsCents: number;
  totalLoansCents: number;
  liquidAssetsCents: number;
  lcrRatio: number;
  computedAt: string;
}

export interface TreasuryNIM {
  nimBps: number;
  interestIncomeCents: number;
  interestExpenseCents: number;
  avgEarningAssetsCents: number;
  computedAt: string;
}

export interface ALMGapBucket {
  bucket: string;
  assetsCents: number;
  liabilitiesCents: number;
  gapCents: number;
}

export interface TreasurySnapshot {
  snapshotId: string;
  snapshotDate: string;
  totalDepositsCents: number;
  totalLoansCents: number;
  liquidAssetsCents: number;
  lcrRatio: number;
  nimBps: number;
  createdAt: string;
  gapBuckets?: ALMGapBucket[];
}

/** Reporting Service (:3011) */
export type ReportType =
  | 'CUSTOMER_STATEMENT'
  | 'DAILY_TRANSACTION_SUMMARY'
  | 'LOAN_PORTFOLIO'
  | 'KYC_STATUS'
  | string;

export type ReportJobStatus = 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED' | string;

export interface ReportJob {
  jobId: string;
  reportType: ReportType;
  customerId: string | null;
  periodStart: string;
  periodEnd: string;
  status: ReportJobStatus;
  fileUrl: string | null;
  errorMessage: string | null;
  requestedBy: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface CreateStatementRequest {
  periodStart: string;
  periodEnd: string;
}

export interface CreateRegulatoryReportRequest {
  reportType: 'DAILY_TRANSACTION_SUMMARY' | 'LOAN_PORTFOLIO' | 'KYC_STATUS';
  periodStart: string;
  periodEnd: string;
}

export interface ReportPage {
  content: ReportJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Payment Rail Service (:3012) */
export type PaymentRail = 'ACH' | 'RTGS' | 'SWIFT_SIM' | string;
export type PaymentDirection = 'OUTBOUND' | 'INBOUND' | string;
export type PaymentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'RETURNED' | string;

export interface ExternalPayment {
  paymentId: string;
  direction: PaymentDirection;
  rail: PaymentRail;
  customerId: string;
  internalAccountId: string;
  externalAccountNumber: string;
  externalRoutingNumber: string;
  externalBankName: string;
  amountCents: number;
  currency: string;
  status: PaymentStatus;
  ledgerTransactionId: string | null;
  failureReason: string | null;
  scheduledAt: string | null;
  completedAt: string | null;
  idempotencyKey?: string;
  createdAt: string;
}

export interface CreateOutboundPaymentRequest {
  rail: PaymentRail;
  fromAccountId: string;
  externalAccountNumber: string;
  externalRoutingNumber: string;
  externalBankName: string;
  amountCents: number;
  idempotencyKey?: string;
}

/** Card Service (:3013) */
export type CardStatus = 'ACTIVE' | 'FROZEN' | 'CANCELLED' | 'EXPIRED' | string;

export interface Card {
  cardId: string;
  customerId: string;
  accountId: string;
  maskedNumber: string;
  expiryMonth: number;
  expiryYear: number;
  status: CardStatus;
  dailyLimitCents: number;
  createdAt: string;
}

export interface CreateCardRequest {
  accountId: string;
}

export interface UpdateCardStatusRequest {
  status: CardStatus;
}
