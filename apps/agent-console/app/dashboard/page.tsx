'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { Card, ErrorBox, PageHeader, Spinner, listCustomersByKyc, listTickets, parseApiError } from '@novo/shared';

const REFRESH_MS = 30_000;

function isToday(value: string): boolean {
  const d = new Date(value);
  const now = new Date();
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}

function Stat({ label, value, loading, error, href }: { label: string; value?: number; loading: boolean; error?: string; href?: string }) {
  const body = (
    <Card>
      <p className="text-sm text-slate-500">{label}</p>
      {loading ? <Spinner /> : null}
      {error ? <p className="mt-1 text-sm text-rose-700">{error}</p> : null}
      {value !== undefined ? <p className="mt-1 text-3xl font-semibold">{value}</p> : null}
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
  // Fraud-flagged transactions today = auto-created "Fraud flag on transaction ..." tickets opened today (newest 100).
  const fraud = useQuery({
    queryKey: ['dash-fraud'],
    queryFn: async () => {
      const page = await listTickets({ size: 100 });
      return page.content.filter((t) => t.category === 'TRANSACTION_DISPUTE' && t.subject.startsWith('Fraud flag') && isToday(t.createdAt)).length;
    },
    refetchInterval: REFRESH_MS,
  });

  return (
    <>
      <PageHeader title="Dashboard" />
      <p className="mb-4 text-xs text-slate-500">Refreshes every 30 seconds.</p>
      <div className="grid gap-4 md:grid-cols-3">
        <Stat label="Open tickets" value={open.data?.totalElements} loading={open.isLoading} error={open.isError ? parseApiError(open.error).message : undefined} href="/tickets" />
        <Stat
          label="Pending KYC"
          value={pendingKyc.data?.totalElements}
          loading={pendingKyc.isLoading}
          error={pendingKyc.isError ? 'Needs GET /api/customers?kycStatus= (see docs/FRONTEND.md)' : undefined}
          href="/kyc-queue"
        />
        <Stat label="Fraud-flagged transactions today" value={fraud.data} loading={fraud.isLoading} error={fraud.isError ? parseApiError(fraud.error).message : undefined} />
      </div>
      {open.isError && pendingKyc.isError && fraud.isError ? <div className="mt-6"><ErrorBox>The backend services are not reachable.</ErrorBox></div> : null}
    </>
  );
}
