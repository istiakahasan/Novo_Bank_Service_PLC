'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Modal,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  formatCents,
  formatDateTime,
  getLoanApplication,
  getLoanCollateral,
  maskUuid,
  parseApiError,
  payRepayment,
  type RepaymentSchedule,
} from '@novo/shared';

export default function LoanApplicationDetailPage({ params }: { params: { id: string } }) {
  const queryClient = useQueryClient();
  const [selectedRepayment, setSelectedRepayment] = useState<RepaymentSchedule | null>(null);

  const applicationQuery = useQuery({
    queryKey: ['loan-application', params.id],
    queryFn: () => getLoanApplication(params.id),
  });

  const app = applicationQuery.data;
  const isAutoOrHome = app?.productCode?.includes('AUTO') || app?.productCode?.includes('HOME');

  const collateralQuery = useQuery({
    queryKey: ['loan-collateral', params.id],
    queryFn: () => getLoanCollateral(params.id),
    enabled: !!app && isAutoOrHome,
  });

  const payMutation = useMutation({
    mutationFn: (repaymentId: string) => payRepayment(repaymentId),
    onSuccess: async () => {
      setSelectedRepayment(null);
      await queryClient.invalidateQueries({ queryKey: ['loan-application', params.id] });
    },
  });

  const getStatusTone = (status: string) => {
    switch (status) {
      case 'APPROVED':
      case 'DISBURSED':
      case 'PAID':
        return 'green';
      case 'REJECTED':
      case 'OVERDUE':
        return 'red';
      case 'UNDER_REVIEW':
      case 'PENDING':
        return 'yellow';
      case 'SUBMITTED':
        return 'blue';
      default:
        return 'gray';
    }
  };

  return (
    <>
      <PageHeader
        title={`Loan Application ${app ? maskUuid(app.applicationId) : ''}`}
        actions={
          <Link href="/loans" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            &larr; Back to Loans
          </Link>
        }
      />

      {applicationQuery.isLoading ? <SkeletonCard rows={6} /> : null}

      {applicationQuery.isError ? (
        <ErrorBox>{parseApiError(applicationQuery.error).message}</ErrorBox>
      ) : null}

      {app ? (
        <div className="space-y-6">
          {/* Main Application Summary Card */}
          <Card title="Application Details">
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3 text-sm">
              <div>
                <span className="text-slate-500">Application ID:</span>
                <p className="font-mono text-slate-800">{app.applicationId}</p>
              </div>
              <div>
                <span className="text-slate-500">Product:</span>
                <p className="font-medium text-slate-900">{app.productName || app.productCode}</p>
              </div>
              <div>
                <span className="text-slate-500">Status:</span>
                <div className="mt-1">
                  <Badge tone={getStatusTone(app.status)}>{app.status}</Badge>
                </div>
              </div>
              <div>
                <span className="text-slate-500">Requested Amount:</span>
                <p className="font-medium text-slate-900">{formatCents(app.requestedAmountCents)}</p>
              </div>
              <div>
                <span className="text-slate-500">Requested Tenure:</span>
                <p className="text-slate-900">{app.requestedTenureMonths} months</p>
              </div>
              <div>
                <span className="text-slate-500">Submission Date:</span>
                <p className="text-slate-900">{formatDateTime(app.createdAt)}</p>
              </div>
            </div>

            {/* Approved Terms Banner */}
            {app.status === 'APPROVED' ? (
              <div className="mt-6 rounded-lg border border-emerald-200 bg-emerald-50 p-4">
                <div className="flex items-center gap-2 text-emerald-800 font-semibold text-base">
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                  </svg>
                  Application Approved — Awaiting disbursement
                </div>
                <div className="mt-3 grid gap-4 sm:grid-cols-3 text-sm text-emerald-900">
                  <div>
                    <span className="text-emerald-700">Approved Amount:</span>
                    <p className="font-semibold text-lg">{formatCents(app.approvedAmountCents)}</p>
                  </div>
                  <div>
                    <span className="text-emerald-700">Approved Rate:</span>
                    <p className="font-semibold text-lg">{((app.approvedInterestRateBps || 0) / 100).toFixed(2)}% APR</p>
                  </div>
                  <div>
                    <span className="text-emerald-700">Approved Tenure:</span>
                    <p className="font-semibold text-lg">{app.approvedTenureMonths} months</p>
                  </div>
                </div>
              </div>
            ) : null}

            {app.status === 'REJECTED' && app.rejectionReason ? (
              <div className="mt-6 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-800">
                <span className="font-semibold">Rejection Reason:</span>
                <p className="mt-1">{app.rejectionReason}</p>
              </div>
            ) : null}
          </Card>

          {/* Repayment Schedule if DISBURSED */}
          {app.status === 'DISBURSED' || (app.repayments && app.repayments.length > 0) ? (
            <Card title="Repayment Schedule">
              {app.repayments && app.repayments.length > 0 ? (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm" aria-label="Repayment Schedule">
                    <caption className="sr-only">Loan repayment schedule and payment history</caption>
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                      <tr>
                        <th scope="col" className="px-4 py-3">Due Date</th>
                        <th scope="col" className="px-4 py-3">Principal Due</th>
                        <th scope="col" className="px-4 py-3">Interest Due</th>
                        <th scope="col" className="px-4 py-3">Total Due</th>
                        <th scope="col" className="px-4 py-3">Status</th>
                        <th scope="col" className="px-4 py-3">Paid Date</th>
                        <th scope="col" className="px-4 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {app.repayments.map((repayment) => (
                        <tr key={repayment.repaymentId} className="hover:bg-slate-50">
                          <td className="px-4 py-3 font-medium text-slate-900">{repayment.dueDate}</td>
                          <td className="px-4 py-3 text-slate-600">{formatCents(repayment.principalDueCents)}</td>
                          <td className="px-4 py-3 text-slate-600">{formatCents(repayment.interestDueCents)}</td>
                          <td className="px-4 py-3 font-semibold text-slate-900">{formatCents(repayment.totalDueCents)}</td>
                          <td className="px-4 py-3">
                            <Badge tone={getStatusTone(repayment.status)}>{repayment.status}</Badge>
                          </td>
                          <td className="px-4 py-3 text-slate-500">
                            {repayment.paidAt ? formatDateTime(repayment.paidAt) : '—'}
                          </td>
                          <td className="px-4 py-3 text-right">
                            {repayment.status === 'PENDING' || repayment.status === 'OVERDUE' ? (
                              <Button
                                variant="primary"
                                onClick={() => setSelectedRepayment(repayment)}
                              >
                                Pay Now
                              </Button>
                            ) : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-sm text-slate-500">No repayment entries found for this loan.</p>
              )}
            </Card>
          ) : null}

          {/* Collateral List for AUTO / HOME Products (Read-Only) */}
          {isAutoOrHome ? (
            <Card title="Pledged Collateral">
              {collateralQuery.isLoading ? <SkeletonTable rows={2} cols={4} /> : null}

              {collateralQuery.isError ? (
                <ErrorBox>{parseApiError(collateralQuery.error).message}</ErrorBox>
              ) : null}

              {collateralQuery.data ? (
                collateralQuery.data.length === 0 ? (
                  <p className="text-sm text-slate-500">No collateral registered for this application.</p>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm" aria-label="Pledged Collateral">
                      <caption className="sr-only">Collateral attached to this secured loan</caption>
                      <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                        <tr>
                          <th scope="col" className="px-4 py-3">Type</th>
                          <th scope="col" className="px-4 py-3">Description</th>
                          <th scope="col" className="px-4 py-3">Estimated Value</th>
                          <th scope="col" className="px-4 py-3">Valuation Date</th>
                          <th scope="col" className="px-4 py-3">Status</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {collateralQuery.data.map((c) => (
                          <tr key={c.collateralId} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-semibold text-slate-800">{c.collateralType}</td>
                            <td className="px-4 py-3 text-slate-700">{c.description}</td>
                            <td className="px-4 py-3 font-medium text-slate-900">{formatCents(c.estimatedValueCents)}</td>
                            <td className="px-4 py-3 text-slate-600">{c.valuationDate}</td>
                            <td className="px-4 py-3">
                              <Badge tone={c.status === 'PLEDGED' ? 'blue' : c.status === 'RELEASED' ? 'green' : 'red'}>
                                {c.status}
                              </Badge>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )
              ) : null}
            </Card>
          ) : null}
        </div>
      ) : null}

      {/* Confirmation Modal for Paying Repayment */}
      <Modal
        isOpen={!!selectedRepayment}
        onClose={() => setSelectedRepayment(null)}
        title="Confirm Repayment"
        description="Verify the repayment details before debiting your account."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setSelectedRepayment(null)}
              disabled={payMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (selectedRepayment) {
                  payMutation.mutate(selectedRepayment.repaymentId);
                }
              }}
              disabled={payMutation.isPending}
            >
              {payMutation.isPending ? 'Processing...' : 'Confirm & Pay'}
            </Button>
          </>
        }
      >
        {selectedRepayment ? (
          <div className="space-y-3 text-sm text-slate-700">
            {payMutation.isError ? (
              <ErrorBox>{parseApiError(payMutation.error).message}</ErrorBox>
            ) : null}
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Due Date:</span>
              <span className="font-semibold text-slate-900">{selectedRepayment.dueDate}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Principal:</span>
              <span className="text-slate-900">{formatCents(selectedRepayment.principalDueCents)}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Interest:</span>
              <span className="text-slate-900">{formatCents(selectedRepayment.interestDueCents)}</span>
            </div>
            <div className="flex justify-between pt-1 text-base font-bold text-slate-900">
              <span>Total Payment:</span>
              <span>{formatCents(selectedRepayment.totalDueCents)}</span>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
