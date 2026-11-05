'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  formatCents,
  formatDateTime,
  getBalance,
  getCustomer,
  getEntries,
  kycTone,
  parseApiError,
  useAuth,
} from '@novo/shared';
import { LinkAccountCard } from '@/components/LinkAccountCard';

export default function DashboardPage() {
  const { accountId, customerId, loading } = useAuth();

  const balance = useQuery({ queryKey: ['balance', accountId], queryFn: () => getBalance(accountId!), enabled: !!accountId });
  const entries = useQuery({ queryKey: ['entries', accountId, 5], queryFn: () => getEntries(accountId!, 5), enabled: !!accountId });
  const customer = useQuery({ queryKey: ['customer', customerId], queryFn: () => getCustomer(customerId!), enabled: !!customerId });

  if (loading) return <Spinner />;

  return (
    <>
      <PageHeader
        title="Dashboard"
        actions={
          <>
            <Link href="/support">
              <Button variant="secondary">Support</Button>
            </Link>
            <Link href="/transfer">
              <Button>New Transfer</Button>
            </Link>
          </>
        }
      />

      {customer.data && customer.data.kycStatus !== 'verified' ? (
        <div className="mb-6">
          <ErrorBox tone="yellow">Your identity verification is {customer.data.kycStatus}. Transfers are blocked until KYC is verified.</ErrorBox>
        </div>
      ) : null}

      {!accountId ? (
        <LinkAccountCard />
      ) : (
        <div className="grid gap-6 md:grid-cols-3">
          <Card title="Balance" className="md:col-span-1">
            {balance.isLoading ? <Spinner /> : null}
            {balance.isError ? <ErrorBox>{parseApiError(balance.error).message}</ErrorBox> : null}
            {balance.data ? <p className="text-3xl font-semibold">{formatCents(balance.data.balanceCents)}</p> : null}
          </Card>

          <Card title="KYC status" className="md:col-span-1">
            {customer.isLoading ? <Spinner /> : null}
            {customer.isError ? <ErrorBox>{parseApiError(customer.error).message}</ErrorBox> : null}
            {customer.data ? <Badge tone={kycTone(customer.data.kycStatus)}>{customer.data.kycStatus.toUpperCase()}</Badge> : null}
          </Card>

          <Card title="Last 5 transactions" className="md:col-span-3">
            {entries.isLoading ? <Spinner /> : null}
            {entries.isError ? <ErrorBox>{parseApiError(entries.error).message}</ErrorBox> : null}
            {entries.data && entries.data.length === 0 ? <p className="text-sm text-slate-500">No transactions yet.</p> : null}
            {entries.data && entries.data.length > 0 ? (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-slate-500">
                    <th className="py-2">Date</th>
                    <th>Type</th>
                    <th className="text-right">Amount</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {entries.data.map((entry) => (
                    <tr key={entry.id} className="border-t border-slate-100">
                      <td className="py-2">{formatDateTime(entry.createdAt)}</td>
                      <td>
                        <Badge tone={entry.direction === 'credit' ? 'green' : 'gray'}>{entry.direction}</Badge>
                      </td>
                      <td className="text-right font-medium">
                        {entry.direction === 'debit' ? '-' : '+'}
                        {formatCents(entry.amountCents)}
                      </td>
                      <td className="text-right">
                        <Link href={`/transactions/${entry.transactionId}`} className="text-sky-700 hover:underline">
                          Details
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : null}
          </Card>
        </div>
      )}
    </>
  );
}
