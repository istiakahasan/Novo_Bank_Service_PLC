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
  listCards,
  listExternalPayments,
  listLoanApplications,
  maskUuid,
  parseApiError,
  useAuth,
} from '@novo/shared';
import { LinkAccountCard } from '@/components/LinkAccountCard';

export default function DashboardPage() {
  const { accountId, customerId, loading } = useAuth();

  const balance = useQuery({
    queryKey: ['balance', accountId],
    queryFn: () => getBalance(accountId!),
    enabled: !!accountId,
  });

  const entries = useQuery({
    queryKey: ['entries', accountId, 5],
    queryFn: () => getEntries(accountId!, 5),
    enabled: !!accountId,
  });

  const customer = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => getCustomer(customerId!),
    enabled: !!customerId,
  });

  const loansQuery = useQuery({
    queryKey: ['my-loan-applications'],
    queryFn: () => listLoanApplications(),
  });

  const cardsQuery = useQuery({
    queryKey: ['cards'],
    queryFn: listCards,
  });

  const externalPaymentsQuery = useQuery({
    queryKey: ['external-payments-history'],
    queryFn: () => listExternalPayments(),
  });

  if (loading) return <Spinner />;

  // Active loans: status DISBURSED or APPROVED
  const activeLoans = loansQuery.data?.filter(
    (l) => l.status === 'DISBURSED' || l.status === 'APPROVED'
  ) || [];
  const activeLoansCount = activeLoans.length;
  const totalLoanOutstandingCents = activeLoans.reduce((sum, loan) => {
    // If repayments present, sum unpaid totalDueCents; otherwise approved/requested amount
    if (loan.repayments && loan.repayments.length > 0) {
      const unpaid = loan.repayments
        .filter((r) => r.status !== 'PAID')
        .reduce((rSum, r) => rSum + r.totalDueCents, 0);
      return sum + unpaid;
    }
    return sum + (loan.approvedAmountCents || loan.requestedAmountCents || 0);
  }, 0);

  const cardCount = cardsQuery.data?.length ?? 0;
  const recentExternalPayments = (externalPaymentsQuery.data || []).slice(0, 3);

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
          <ErrorBox tone="yellow">
            Your identity verification is {customer.data.kycStatus}. Transfers are blocked until KYC is verified.
          </ErrorBox>
        </div>
      ) : null}

      {!accountId ? (
        <LinkAccountCard />
      ) : (
        <div className="space-y-6">
          {/* Top Metric Cards */}
          <div className="grid gap-6 md:grid-cols-3">
            <Card title="Primary Account Balance">
              {balance.isLoading ? <Spinner /> : null}
              {balance.isError ? <ErrorBox>{parseApiError(balance.error).message}</ErrorBox> : null}
              {balance.data ? (
                <p className="text-3xl font-semibold text-slate-900">{formatCents(balance.data.balanceCents)}</p>
              ) : null}
            </Card>

            <Card title="KYC Status">
              {customer.isLoading ? <Spinner /> : null}
              {customer.isError ? <ErrorBox>{parseApiError(customer.error).message}</ErrorBox> : null}
              {customer.data ? (
                <div className="flex items-center gap-2">
                  <Badge tone={kycTone(customer.data.kycStatus)}>{customer.data.kycStatus.toUpperCase()}</Badge>
                  <span className="text-xs text-slate-500">Tier: {customer.data.amlRiskRating.toUpperCase()}</span>
                </div>
              ) : null}
            </Card>

            <Card title="Cards">
              {cardsQuery.isLoading ? <Spinner /> : null}
              {cardsQuery.data ? (
                <div className="flex items-baseline justify-between">
                  <p className="text-3xl font-semibold text-slate-900">{cardCount}</p>
                  <Link href="/cards" className="text-sm text-sky-600 hover:underline">
                    Manage cards &rarr;
                  </Link>
                </div>
              ) : null}
            </Card>
          </div>

          {/* New Summary Cards for Loans & External Payments */}
          <div className="grid gap-6 md:grid-cols-2">
            {/* Active Loans Summary Card */}
            <Card title="Active Loans">
              {loansQuery.isLoading ? <Spinner /> : null}
              {loansQuery.isError ? <ErrorBox>{parseApiError(loansQuery.error).message}</ErrorBox> : null}
              {loansQuery.data ? (
                <div className="space-y-3">
                  <div className="flex items-baseline justify-between">
                    <div>
                      <span className="text-3xl font-semibold text-slate-900">{activeLoansCount}</span>
                      <span className="ml-2 text-sm text-slate-500">active {activeLoansCount === 1 ? 'loan' : 'loans'}</span>
                    </div>
                    <Link href="/loans" className="text-sm text-sky-600 hover:underline">
                      View all &rarr;
                    </Link>
                  </div>
                  <div className="rounded-md bg-slate-50 p-3 text-sm">
                    <span className="text-slate-500">Total Outstanding:</span>
                    <p className="text-xl font-bold text-slate-900">{formatCents(totalLoanOutstandingCents)}</p>
                  </div>
                </div>
              ) : null}
            </Card>

            {/* Recent External Payments Summary Card */}
            <Card title="Recent External Payments">
              {externalPaymentsQuery.isLoading ? <Spinner /> : null}
              {externalPaymentsQuery.isError ? <ErrorBox>{parseApiError(externalPaymentsQuery.error).message}</ErrorBox> : null}
              {externalPaymentsQuery.data ? (
                <div>
                  <div className="flex items-baseline justify-between mb-3">
                    <span className="text-xs text-slate-500">Last 3 external rail transactions</span>
                    <Link href="/external-payments" className="text-sm text-sky-600 hover:underline">
                      All payments &rarr;
                    </Link>
                  </div>
                  {recentExternalPayments.length === 0 ? (
                    <p className="text-sm text-slate-500">No external payments initiated yet.</p>
                  ) : (
                    <ul className="divide-y divide-slate-100 text-sm">
                      {recentExternalPayments.map((p) => (
                        <li key={p.paymentId} className="py-2 flex items-center justify-between">
                          <div>
                            <span className="font-medium text-slate-900">{p.externalBankName || p.rail}</span>
                            <span className="ml-2 text-xs text-slate-500">{p.direction}</span>
                          </div>
                          <div className="text-right">
                            <span className="font-semibold text-slate-900">{formatCents(p.amountCents)}</span>
                            <span className="ml-2 text-xs font-mono text-slate-400">({p.status})</span>
                          </div>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ) : null}
            </Card>
          </div>

          {/* Recent Internal Transactions */}
          <Card title="Recent Internal Account Activity">
            {entries.isLoading ? <Spinner /> : null}
            {entries.isError ? <ErrorBox>{parseApiError(entries.error).message}</ErrorBox> : null}
            {entries.data && entries.data.length === 0 ? <p className="text-sm text-slate-500">No transactions yet.</p> : null}
            {entries.data && entries.data.length > 0 ? (
              <table className="w-full text-sm" aria-label="Recent Account Transactions">
                <caption className="sr-only">Recent ledger entries for primary account</caption>
                <thead>
                  <tr className="text-left text-slate-500">
                    <th scope="col" className="py-2">Date</th>
                    <th scope="col">Type</th>
                    <th scope="col" className="text-right">Amount</th>
                    <th scope="col" />
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
