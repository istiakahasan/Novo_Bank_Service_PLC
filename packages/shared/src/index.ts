export * from './api/client';
export * from './api/customer';
export * from './api/ledger';
export * from './api/support';
export * from './api/loans';
export * from './api/treasury';
export * from './api/reports';
export * from './api/externalPayments';
export * from './api/cards';
export * from './auth/AuthContext';
export * from './providers/Providers';
export * from './utils/masking';
export * from './utils/currency';
export * from './utils/errors';
export * from './utils/roleGuard';
export * from './components/SessionTimeoutProvider';
export * from './components/Skeleton';
export * from './components/EmptyState';
export * from './components/Modal';
export * from './ui/NavBar';
export * from './ui/LoginForm';

// Re-export all types from types/index.ts
export type {
  Role,
  KycStatus,
  TicketCategory,
  TicketPriority,
  TicketStatus,
  SenderType,
  CustomerResponse,
  KycUpdateRequest,
  CustomerPage,
  TransferRequest,
  TransferResponse,
  BalanceResponse,
  LedgerEntryResponse,
  CreateAccountRequest,
  AccountResponse,
  TicketMessageResponse,
  TicketResponse,
  TicketListResponse,
  CreateTicketRequest,
  UpdateTicketRequest,
  CreateMessageRequest,
  TicketListParams,
  SessionInfo,
  ApiErrorBody,
  LoanProduct,
  RepaymentSchedule,
  LoanApplicationStatus,
  LoanApplication,
  CreateLoanApplicationRequest,
  ReviewLoanRequest,
  CollateralType,
  CollateralStatus,
  Collateral,
  AddCollateralRequest,
  OverdueRepayment,
  CollectionActionType,
  CollectionAction,
  CollectionActionRequest,
  TreasuryLiquidity,
  TreasuryNIM,
  ALMGapBucket,
  TreasurySnapshot,
  ReportType,
  ReportJobStatus,
  ReportJob,
  CreateStatementRequest,
  CreateRegulatoryReportRequest,
  ReportPage,
  PaymentRail,
  PaymentDirection,
  PaymentStatus,
  ExternalPayment,
  CreateOutboundPaymentRequest,
  CardStatus,
  CreateCardRequest,
  UpdateCardStatusRequest,
} from './types';

// Re-export UI elements
export {
  Badge,
  PageHeader,
  Spinner,
  ErrorBox,
  Button,
  Field,
  Pagination,
  inputClass,
  formatDateTime,
  kycTone,
  priorityTone,
  statusTone,
} from './ui';

// Merge Card UI component (value) and Card DTO (type) under single export
import { Card as CardComponent } from './ui';
import type { Card as CardDataType } from './types';

type Card = CardDataType;
const Card = CardComponent;

export { Card };
