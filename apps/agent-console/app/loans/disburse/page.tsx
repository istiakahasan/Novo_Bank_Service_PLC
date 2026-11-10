'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  EmptyState,
  ErrorBox,
  Modal,
  PageHeader,
  SkeletonTable,
  disburseLoan,
  formatCents,
  formatDateTime,
  getCustomerAccounts,
  listLoanApplications,
  maskUuid,
  parseApiError,
  type LoanApplication,
} from '@novo/shared';

export default function LoanDisbursePage() {
  const queryClient = useQueryClient();
  const [appToDisburse, setAppToDisburse] = useState<LoanApplication | null>(null);

  const approvedQuery = useQuery({
    queryKey: ['loan-applications', 'APPROVED'],
    queryFn: () => listLoanApplications({ status: 'APPROVED' }),
  });

  const accountsQuery = useQuery({
    queryKey: ['customer-accounts', appToDisburse?.customerId],
    queryFn: () => getCustomerAccounts(appToDisburse!.customerId),
    enabled: !!appToDisburse?.customerId,
  });

  const disburseMutation = useMutation({
    mutationFn: (applicationId: string) => disburseLoan(applicationId),
    onSuccess: async () => {
      setAppToDisburse(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['loan-applications', 'APPROVED'] }),
        queryClient.invalidateQueries({ queryKey: ['loan-applications', 'DISBURSED'] }),
      ]);
    },
  });

  const primaryAccount = accountsQuery.data?.[0];

  return (
    <>
      <PageHeader
        title="Loan Disbursement Queue"
        actions={
          <Link href="/loans/review">
            <Button variant="secondary">&larr; Back to Reviews</Button>
          </Link>
        }
      />

      {approvedQuery.isLoading ? <SkeletonTable rows={4} cols={6} /> : null}

      {approvedQuery.isError ? (
        <ErrorBox>{parseApiError(approvedQuery.error).message}</ErrorBox>
      ) : null}

      {approvedQuery.data ? (
        approvedQuery.data.length === 0 ? (
          <EmptyState
            title="No approved loans awaiting disbursement"
            description="All approved loans have been disbursed to customer accounts."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm" aria-label="Approved Loans Ready for Disbursement">
              <caption className="sr-only">Loans approved by staff awaiting fund disbursement</caption>
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">App ID</th>
                  <th scope="col" className="px-4 py-3">Customer ID</th>
                  <th scope="col" className="px-4 py-3">Product</th>
                  <th scope="col" className="px-4 py-3">Approved Amount</th>
                  <th scope="col" className="px-4 py-3">Approved Tenure</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {approvedQuery.data.map((app) => (
                  <tr key={app.applicationId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-slate-700">
                      <Link href={`/loans/${app.applicationId}`} className="text-sky-600 hover:underline">
                        {maskUuid(app.applicationId)}
                      </Link>
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-700">{maskUuid(app.customerId)}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{app.productName || app.productCode}</td>
                    <td className="px-4 py-3 font-bold text-slate-900">{formatCents(app.approvedAmountCents)}</td>
                    <td className="px-4 py-3 text-slate-600">{app.approvedTenureMonths} months</td>
                    <td className="px-4 py-3">
                      <Badge tone="green">{app.status}</Badge>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button onClick={() => setAppToDisburse(app)}>Disburse Funds</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      {/* Disburse Confirmation Modal */}
      <Modal
        isOpen={!!appToDisburse}
        onClose={() => setAppToDisburse(null)}
        title="Confirm Loan Disbursement"
        description="Verify destination account and approved amount before releasing principal funds."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setAppToDisburse(null)}
              disabled={disburseMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (appToDisburse) {
                  disburseMutation.mutate(appToDisburse.applicationId);
                }
              }}
              disabled={disburseMutation.isPending}
            >
              {disburseMutation.isPending ? 'Disbursing...' : 'Confirm Disbursement'}
            </Button>
          </>
        }
      >
        {appToDisburse ? (
          <div className="space-y-3 text-sm text-slate-700">
            {disburseMutation.isError ? (
              <ErrorBox>{parseApiError(disburseMutation.error).message}</ErrorBox>
            ) : null}

            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Loan ID:</span>
              <span className="font-mono text-slate-900">{maskUuid(appToDisburse.applicationId)}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Applicant:</span>
              <span className="font-mono text-slate-900">{maskUuid(appToDisburse.customerId)}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Disbursement Account:</span>
              <span className="font-mono font-medium text-slate-900">
                {primaryAccount
                  ? `${primaryAccount.accountType.toUpperCase()} (${primaryAccount.accountNumber})`
                  : accountsQuery.isLoading
                  ? 'Loading account...'
                  : 'Customer Primary Account'}
              </span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Interest Rate:</span>
              <span className="font-medium text-slate-900">
                {((appToDisburse.approvedInterestRateBps || 0) / 100).toFixed(2)}% APR
              </span>
            </div>
            <div className="flex justify-between pt-1 text-base font-bold text-slate-900">
              <span>Amount to Credit:</span>
              <span className="text-emerald-700">{formatCents(appToDisburse.approvedAmountCents)}</span>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}

