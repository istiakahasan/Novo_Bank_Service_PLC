'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBox,
  Field,
  PageHeader,
  SkeletonTable,
  Spinner,
  dollarsToCents,
  formatCents,
  formatDateTime,
  getCustomer,
  inputClass,
  kycTone,
  listLoanApplications,
  maskUuid,
  parseApiError,
  reviewLoanApplication,
  type LoanApplication,
} from '@novo/shared';
import { loanReviewSchema, type LoanReviewFormValues } from './schema';

export default function LoanReviewPage() {
  const queryClient = useQueryClient();
  const [selectedApp, setSelectedApp] = useState<LoanApplication | null>(null);

  const submittedQuery = useQuery({
    queryKey: ['loan-applications', 'SUBMITTED'],
    queryFn: () => listLoanApplications({ status: 'SUBMITTED' }),
  });

  const underReviewQuery = useQuery({
    queryKey: ['loan-applications', 'UNDER_REVIEW'],
    queryFn: () => listLoanApplications({ status: 'UNDER_REVIEW' }),
  });

  const mergedApplications = useMemo(() => {
    const list = [...(submittedQuery.data || []), ...(underReviewQuery.data || [])];
    return list.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [submittedQuery.data, underReviewQuery.data]);

  const customerQuery = useQuery({
    queryKey: ['customer', selectedApp?.customerId],
    queryFn: () => getCustomer(selectedApp!.customerId),
    enabled: !!selectedApp?.customerId,
  });

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<LoanReviewFormValues>({
    resolver: zodResolver(loanReviewSchema),
    defaultValues: {
      action: 'APPROVE',
      approvedAmount: '',
      approvedInterestRateBps: 850,
      approvedTenureMonths: 12,
      rejectionReason: '',
    },
  });

  const selectedAction = watch('action');

  const openReviewPanel = (app: LoanApplication) => {
    setSelectedApp(app);
    reset({
      action: 'APPROVE',
      approvedAmount: (app.requestedAmountCents / 100).toFixed(2),
      approvedInterestRateBps: 850,
      approvedTenureMonths: app.requestedTenureMonths,
      rejectionReason: '',
    });
  };

  const reviewMutation = useMutation({
    mutationFn: async (values: LoanReviewFormValues) => {
      if (!selectedApp) throw new Error('No application selected');

      const body = {
        action: values.action,
        approvedAmountCents:
          values.action === 'APPROVE' ? dollarsToCents(values.approvedAmount!)! : undefined,
        approvedInterestRateBps:
          values.action === 'APPROVE' ? Number(values.approvedInterestRateBps) : undefined,
        approvedTenureMonths:
          values.action === 'APPROVE' ? Number(values.approvedTenureMonths) : undefined,
        rejectionReason: values.action === 'REJECT' ? values.rejectionReason : undefined,
      };

      return await reviewLoanApplication(selectedApp.applicationId, body);
    },
    onSuccess: async () => {
      setSelectedApp(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['loan-applications', 'SUBMITTED'] }),
        queryClient.invalidateQueries({ queryKey: ['loan-applications', 'UNDER_REVIEW'] }),
        queryClient.invalidateQueries({ queryKey: ['loan-applications', 'APPROVED'] }),
      ]);
    },
  });

  const onSubmitReview = (values: LoanReviewFormValues) => {
    reviewMutation.mutate(values);
  };

  const isLoading = submittedQuery.isLoading || underReviewQuery.isLoading;
  const isError = submittedQuery.isError || underReviewQuery.isError;
  const errorObj = submittedQuery.error || underReviewQuery.error;

  return (
    <>
      <PageHeader
        title="Loan Application Reviews"
        actions={
          <Link href="/loans/disburse">
            <Button variant="secondary">Go to Disbursement Queue &rarr;</Button>
          </Link>
        }
      />

      {isLoading ? <SkeletonTable rows={5} cols={6} /> : null}
      {isError ? <ErrorBox>{parseApiError(errorObj).message}</ErrorBox> : null}

      {!isLoading && !isError ? (
        mergedApplications.length === 0 ? (
          <EmptyState
            title="No loan applications to review"
            description="There are currently no submitted or pending review applications in the queue."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm" aria-label="Loan Applications Pending Review">
              <caption className="sr-only">Applications pending staff review and credit approval</caption>
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">App ID</th>
                  <th scope="col" className="px-4 py-3">Customer ID</th>
                  <th scope="col" className="px-4 py-3">Product</th>
                  <th scope="col" className="px-4 py-3">Requested Amount</th>
                  <th scope="col" className="px-4 py-3">Tenure</th>
                  <th scope="col" className="px-4 py-3">Status</th>
                  <th scope="col" className="px-4 py-3">Submitted</th>
                  <th scope="col" className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {mergedApplications.map((app) => (
                  <tr key={app.applicationId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-slate-700">
                      <Link href={`/loans/${app.applicationId}`} className="text-sky-600 hover:underline">
                        {maskUuid(app.applicationId)}
                      </Link>
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-700">{maskUuid(app.customerId)}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{app.productName || app.productCode}</td>
                    <td className="px-4 py-3 font-semibold text-slate-900">{formatCents(app.requestedAmountCents)}</td>
                    <td className="px-4 py-3 text-slate-600">{app.requestedTenureMonths} mo</td>
                    <td className="px-4 py-3">
                      <Badge tone={app.status === 'UNDER_REVIEW' ? 'yellow' : 'blue'}>{app.status}</Badge>
                    </td>
                    <td className="px-4 py-3 text-slate-500">{formatDateTime(app.createdAt)}</td>
                    <td className="px-4 py-3 text-right">
                      <Button onClick={() => openReviewPanel(app)}>Review</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      {/* Review Slide-over / Side Panel */}
      {selectedApp ? (
        <div className="fixed inset-0 z-50 overflow-hidden" role="dialog" aria-modal="true" aria-labelledby="review-panel-title">
          <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm transition-opacity" onClick={() => setSelectedApp(null)} />
          <div className="fixed inset-y-0 right-0 flex max-w-full pl-10">
            <div className="w-screen max-w-md bg-white p-6 shadow-xl overflow-y-auto">
              <div className="flex items-center justify-between border-b pb-4">
                <h2 id="review-panel-title" className="text-lg font-semibold text-slate-900">
                  Review Loan Application
                </h2>
                <button
                  type="button"
                  onClick={() => setSelectedApp(null)}
                  className="rounded-lg p-1 text-slate-400 hover:bg-slate-100"
                  aria-label="Close review panel"
                >
                  <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>

              {/* Customer Profile Facts */}
              <div className="mt-4 rounded-lg bg-slate-50 p-4 space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-slate-500">Applicant:</span>
                  <span className="font-semibold text-slate-900">{customerQuery.data?.displayName || 'Loading...'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">KYC Status:</span>
                  {customerQuery.data ? (
                    <Badge tone={kycTone(customerQuery.data.kycStatus)}>
                      {customerQuery.data.kycStatus.toUpperCase()}
                    </Badge>
                  ) : (
                    <span>...</span>
                  )}
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">AML Risk Rating:</span>
                  {customerQuery.data ? (
                    <Badge tone={customerQuery.data.amlRiskRating === 'high' ? 'red' : customerQuery.data.amlRiskRating === 'medium' ? 'yellow' : 'green'}>
                      {customerQuery.data.amlRiskRating.toUpperCase()}
                    </Badge>
                  ) : (
                    <span>...</span>
                  )}
                </div>
                <div className="flex justify-between border-t pt-2">
                  <span className="text-slate-500">Requested Amount:</span>
                  <span className="font-semibold text-slate-900">{formatCents(selectedApp.requestedAmountCents)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Requested Tenure:</span>
                  <span className="text-slate-900">{selectedApp.requestedTenureMonths} months</span>
                </div>
              </div>

              {/* Review Decision Form */}
              <form onSubmit={handleSubmit(onSubmitReview)} className="mt-6 space-y-4" noValidate>
                {reviewMutation.isError ? (
                  <ErrorBox>{parseApiError(reviewMutation.error).message}</ErrorBox>
                ) : null}

                <Field label="Credit Decision" error={errors.action?.message}>
                  <div className="grid grid-cols-2 gap-3">
                    <label
                      className={`flex cursor-pointer items-center justify-center rounded-lg border p-3 font-semibold text-sm transition ${
                        selectedAction === 'APPROVE'
                          ? 'border-emerald-600 bg-emerald-50 text-emerald-800'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      <input type="radio" value="APPROVE" {...register('action')} className="sr-only" />
                      Approve Loan
                    </label>
                    <label
                      className={`flex cursor-pointer items-center justify-center rounded-lg border p-3 font-semibold text-sm transition ${
                        selectedAction === 'REJECT'
                          ? 'border-rose-600 bg-rose-50 text-rose-800'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      <input type="radio" value="REJECT" {...register('action')} className="sr-only" />
                      Reject Loan
                    </label>
                  </div>
                </Field>

                {selectedAction === 'APPROVE' ? (
                  <>
                    <Field label="Approved Amount (USD)" error={errors.approvedAmount?.message}>
                      <input type="text" {...register('approvedAmount')} className={inputClass} />
                    </Field>
                    <Field label="Approved Interest Rate (bps)" error={errors.approvedInterestRateBps?.message}>
                      <input
                        type="number"
                        placeholder="e.g. 750 for 7.50%"
                        {...register('approvedInterestRateBps', { valueAsNumber: true })}
                        className={inputClass}
                      />
                    </Field>
                    <Field label="Approved Tenure (Months)" error={errors.approvedTenureMonths?.message}>
                      <input
                        type="number"
                        {...register('approvedTenureMonths', { valueAsNumber: true })}
                        className={inputClass}
                      />
                    </Field>
                  </>
                ) : (
                  <Field label="Rejection Reason" error={errors.rejectionReason?.message}>
                    <textarea
                      rows={4}
                      placeholder="Specify credit or regulatory reason for rejection..."
                      {...register('rejectionReason')}
                      className={inputClass}
                    />
                  </Field>
                )}

                <div className="mt-6 flex justify-end gap-3 pt-4 border-t">
                  <Button variant="secondary" type="button" onClick={() => setSelectedApp(null)}>
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    variant={selectedAction === 'APPROVE' ? 'primary' : 'danger'}
                    disabled={isSubmitting || reviewMutation.isPending}
                  >
                    {isSubmitting || reviewMutation.isPending ? 'Submitting...' : `Confirm ${selectedAction}`}
                  </Button>
                </div>
              </form>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
