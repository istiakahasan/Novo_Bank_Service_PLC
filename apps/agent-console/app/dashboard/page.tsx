'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  getOverdueRepayments,
  getTreasuryLiquidity,
  listCustomersByKyc,
  listLoanApplications,
  listTickets,
  parseApiError,
} from '@novo/shared';

const REFRESH_MS = 30_000;

function isToday(value: string): boolean {
  const d = new Date(value);
  const now = new Date();
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}

function Stat({
  label,
  value,
  loading,
  error,
  href,
  valueColor,
}: {
  label: string;
  value?: string | number;
  loading: boolean;
  error?: string;
  href?: string;
  valueColor?: string;
}) {
  const body = (
    <Card className="hover:border-slate-300 transition">
      <p className="text-sm text-slate-500">{label}</p>
      {loading ? <Spinner /> : null}
      {error ? <p className="mt-1 text-xs text-rose-700">{error}</p> : null}
      {value !== undefined ? (
        <p className={`mt-1 text-3xl font-semibold ${valueColor || 'text-slate-900'}`}>{value}</p>
      ) : null}
    </Card>
  );
  return href ? <Link href={href}>{body}</Link> : body;
}

export default function DashboardPage() {
  const open = useQuery({
    queryKey: ['dash-open'],
    queryFn: () => listTickets({ status: 'OPEN', size: 1 }),
    refetchInterval: REFRESH_MS,
  });

  const pendingKyc = useQuery({
    queryKey: ['dash-kyc'],
    queryFn: () => listCustomersByKyc('pending', 0, 1),
    refetchInterval: REFRESH_MS,
    retry: false,
  });

  const fraud = useQuery({
    queryKey: ['dash-fraud'],
    queryFn: async () => {
      const page = await listTickets({ size: 100 });
      return page.content.filter(
        (t) => t.category === 'TRANSACTION_DISPUTE' && t.subject.startsWith('Fraud flag') && isToday(t.createdAt)
      ).length;
    },
    refetchInterval: REFRESH_MS,
  });

  // 3 New Summary Queries:
  // 1. Pending Loan Reviews (SUBMITTED + UNDER_REVIEW)
  const pendingLoansQuery = useQuery({
    queryKey: ['dash-pending-loans'],
    queryFn: async () => {
      const [submitted, underReview] = await Promise.all([
        listLoanApplications({ status: 'SUBMITTED' }),
        listLoanApplications({ status: 'UNDER_REVIEW' }),
      ]);
      return (submitted?.length || 0) + (underReview?.length || 0);
    },
    refetchInterval: REFRESH_MS,
  });

  // 2. Overdue Repayments
  const overdueRepaymentsQuery = useQuery({
    queryKey: ['dash-overdue-repayments'],
    queryFn: async () => {
      const overdue = await getOverdueRepayments();
      return overdue?.length || 0;
    },
    refetchInterval: REFRESH_MS,
  });

  // 3. LCR Ratio (from Treasury)
  const treasuryQuery = useQuery({
    queryKey: ['dash-treasury-lcr'],
    queryFn: getTreasuryLiquidity,
    refetchInterval: REFRESH_MS,
  });

  const lcrVal = treasuryQuery.data?.lcrRatio !== undefined ? Number(treasuryQuery.data.lcrRatio) : undefined;
  const lcrFormatted = lcrVal !== undefined ? lcrVal.toFixed(4) : undefined;
  const lcrColor =
    lcrVal !== undefined
      ? lcrVal >= 1.0
        ? 'text-emerald-600'
        : lcrVal >= 0.5
        ? 'text-amber-600'
        : 'text-rose-600'
      : undefined;

  return (
    <>
      <PageHeader title="Agent Operations Dashboard" />
      <p className="mb-4 text-xs text-slate-500">Auto-refreshes every 30 seconds.</p>

      {/* Primary Row: Support & KYC */}
      <div className="grid gap-4 md:grid-cols-3">
        <Stat
          label="Open tickets"
          value={open.data?.totalElements}
          loading={open.isLoading}
          error={open.isError ? parseApiError(open.error).message : undefined}
          href="/tickets"
        />
        <Stat
          label="Pending KYC"
          value={pendingKyc.data?.totalElements}
          loading={pendingKyc.isLoading}
          error={pendingKyc.isError ? 'Needs GET /api/customers?kycStatus=' : undefined}
          href="/kyc-queue"
        />
        <Stat
          label="Fraud-flagged transactions today"
          value={fraud.data}
          loading={fraud.isLoading}
          error={fraud.isError ? parseApiError(fraud.error).message : undefined}
        />
      </div>

      {/* Secondary Row: Credit & Treasury Summary Cards */}
      <div className="mt-6 grid gap-4 md:grid-cols-3">
        <Stat
          label="Pending Loan Reviews"
          value={pendingLoansQuery.data}
          loading={pendingLoansQuery.isLoading}
          error={pendingLoansQuery.isError ? parseApiError(pendingLoansQuery.error).message : undefined}
          href="/loans/review"
        />
        <Stat
          label="Overdue Repayments"
          value={overdueRepaymentsQuery.data}
          loading={overdueRepaymentsQuery.isLoading}
          error={overdueRepaymentsQuery.isError ? parseApiError(overdueRepaymentsQuery.error).message : undefined}
          href="/collections"
          valueColor={overdueRepaymentsQuery.data && overdueRepaymentsQuery.data > 0 ? 'text-rose-600' : undefined}
        />
        <Stat
          label="LCR Ratio (Treasury)"
          value={lcrFormatted}
          loading={treasuryQuery.isLoading}
          error={treasuryQuery.isError ? parseApiError(treasuryQuery.error).message : undefined}
          href="/treasury"
          valueColor={lcrColor}
        />
      </div>

      {open.isError && pendingKyc.isError && fraud.isError ? (
        <div className="mt-6">
          <ErrorBox>The backend services are not reachable.</ErrorBox>
        </div>
      ) : null}
    </>
  );
}
