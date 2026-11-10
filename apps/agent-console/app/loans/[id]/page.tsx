'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBox,
  Field,
  Modal,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  addLoanCollateral,
  dollarsToCents,
  formatCents,
  formatDateTime,
  getCustomer,
  getLoanApplication,
  getLoanCollateral,
  inputClass,
  maskUuid,
  parseApiError,
  releaseCollateral,
  type Collateral,
  type CollateralType,
} from '@novo/shared';

interface AddCollateralFormValues {
  collateralType: CollateralType;
  description: string;
  estimatedValue: string;
  valuationDate: string;
}

export default function AgentLoanDetailPage({ params }: { params: { id: string } }) {
  const queryClient = useQueryClient();

  const [isAddCollateralOpen, setIsAddCollateralOpen] = useState(false);
  const [collateralToRelease, setCollateralToRelease] = useState<Collateral | null>(null);

  const applicationQuery = useQuery({
    queryKey: ['agent-loan-application', params.id],
    queryFn: () => getLoanApplication(params.id),
  });

  const app = applicationQuery.data;
  const isAutoOrHome = app?.productCode?.includes('AUTO') || app?.productCode?.includes('HOME');

  const customerQuery = useQuery({
    queryKey: ['customer', app?.customerId],
    queryFn: () => getCustomer(app!.customerId),
    enabled: !!app?.customerId,
  });

  const collateralQuery = useQuery({
    queryKey: ['agent-loan-collateral', params.id],
    queryFn: () => getLoanCollateral(params.id),
    enabled: !!app,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { isSubmitting },
  } = useForm<AddCollateralFormValues>({
    defaultValues: {
      collateralType: 'VEHICLE',
      description: '',
      estimatedValue: '',
      valuationDate: new Date().toISOString().split('T')[0],
    },
  });

  const addCollateralMutation = useMutation({
    mutationFn: async (values: AddCollateralFormValues) => {
      const cents = dollarsToCents(values.estimatedValue);
      if (!cents) throw new Error('Valid dollar amount is required');

      return await addLoanCollateral(params.id, {
        collateralType: values.collateralType,
        description: values.description,
        estimatedValueCents: cents,
        valuationDate: values.valuationDate,
      });
    },
    onSuccess: async () => {
      setIsAddCollateralOpen(false);
      reset();
      await queryClient.invalidateQueries({ queryKey: ['agent-loan-collateral', params.id] });
    },
  });

  const releaseMutation = useMutation({
    mutationFn: (collateralId: string) => releaseCollateral(collateralId),
    onSuccess: async () => {
      setCollateralToRelease(null);
      await queryClient.invalidateQueries({ queryKey: ['agent-loan-collateral', params.id] });
    },
  });

  const canAddCollateral =
    (app?.status === 'APPROVED' || app?.status === 'DISBURSED') && isAutoOrHome;
  const canReleaseCollateral = app?.status === 'CLOSED';

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
        title={`Loan File: ${app ? maskUuid(app.applicationId) : ''}`}
        actions={
          <div className="flex gap-2">
            <Link href="/loans/review">
              <Button variant="secondary">&larr; Review Queue</Button>
            </Link>
            <Link href="/loans/disburse">
              <Button variant="secondary">Disburse Queue &rarr;</Button>
            </Link>
          </div>
        }
      />

      {applicationQuery.isLoading ? <SkeletonCard rows={6} /> : null}

      {applicationQuery.isError ? (
        <ErrorBox>{parseApiError(applicationQuery.error).message}</ErrorBox>
      ) : null}

      {app ? (
        <div className="space-y-6">
          {/* Header Card */}
          <Card title="Loan Summary">
            <div className="grid gap-6 md:grid-cols-3 text-sm">
              <div>
                <span className="text-slate-500">Applicant:</span>
                <p className="font-semibold text-slate-900">{customerQuery.data?.displayName || maskUuid(app.customerId)}</p>
                <p className="font-mono text-xs text-slate-400">{app.customerId}</p>
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
                <span className="text-slate-500">Approved Amount:</span>
                <p className="font-bold text-slate-900">{app.approvedAmountCents ? formatCents(app.approvedAmountCents) : '—'}</p>
              </div>
              <div>
                <span className="text-slate-500">Approved Rate / Tenure:</span>
                <p className="font-medium text-slate-900">
                  {app.approvedInterestRateBps ? `${((app.approvedInterestRateBps) / 100).toFixed(2)}% APR` : '—'} /{' '}
                  {app.approvedTenureMonths ? `${app.approvedTenureMonths} mo` : '—'}
                </p>
              </div>
            </div>
          </Card>

          {/* Repayment Schedule */}
          <Card title="Amortization & Repayment Schedule">
            {app.repayments && app.repayments.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm" aria-label="Loan Amortization Schedule">
                  <caption className="sr-only">Repayment schedule and status</caption>
                  <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                    <tr>
                      <th scope="col" className="px-4 py-3">Due Date</th>
                      <th scope="col" className="px-4 py-3">Principal Due</th>
                      <th scope="col" className="px-4 py-3">Interest Due</th>
                      <th scope="col" className="px-4 py-3">Total Due</th>
                      <th scope="col" className="px-4 py-3">Status</th>
                      <th scope="col" className="px-4 py-3">Paid Date</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {app.repayments.map((r) => (
                      <tr key={r.repaymentId} className="hover:bg-slate-50">
                        <td className="px-4 py-3 font-medium text-slate-900">{r.dueDate}</td>
                        <td className="px-4 py-3 text-slate-600">{formatCents(r.principalDueCents)}</td>
                        <td className="px-4 py-3 text-slate-600">{formatCents(r.interestDueCents)}</td>
                        <td className="px-4 py-3 font-semibold text-slate-900">{formatCents(r.totalDueCents)}</td>
                        <td className="px-4 py-3">
                          <Badge tone={getStatusTone(r.status)}>{r.status}</Badge>
                        </td>
                        <td className="px-4 py-3 text-slate-500">{r.paidAt ? formatDateTime(r.paidAt) : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-sm text-slate-500">No repayment schedule generated yet (generated upon disbursement).</p>
            )}
          </Card>

          {/* Collateral Section */}
          <Card
            title="Collateral Management"
            className="border-slate-200"
          >
            <div className="mb-4 flex items-center justify-between">
              <span className="text-sm text-slate-500">Assets pledged to secure this credit facility.</span>
              {canAddCollateral ? (
                <Button onClick={() => setIsAddCollateralOpen(true)}>+ Add Collateral</Button>
              ) : null}
            </div>

            {collateralQuery.isLoading ? <SkeletonTable rows={2} cols={5} /> : null}

            {collateralQuery.isError ? (
              <ErrorBox>{parseApiError(collateralQuery.error).message}</ErrorBox>
            ) : null}

            {collateralQuery.data ? (
              collateralQuery.data.length === 0 ? (
                <p className="text-sm text-slate-500">No collateral registered for this loan.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm" aria-label="Collateral Register">
                    <caption className="sr-only">List of pledged collateral assets</caption>
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                      <tr>
                        <th scope="col" className="px-4 py-3">Type</th>
                        <th scope="col" className="px-4 py-3">Description</th>
                        <th scope="col" className="px-4 py-3">Estimated Value</th>
                        <th scope="col" className="px-4 py-3">Valuation Date</th>
                        <th scope="col" className="px-4 py-3">Status</th>
                        <th scope="col" className="px-4 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {collateralQuery.data.map((col) => (
                        <tr key={col.collateralId} className="hover:bg-slate-50">
                          <td className="px-4 py-3 font-semibold text-slate-800">{col.collateralType}</td>
                          <td className="px-4 py-3 text-slate-700">{col.description}</td>
                          <td className="px-4 py-3 font-medium text-slate-900">{formatCents(col.estimatedValueCents)}</td>
                          <td className="px-4 py-3 text-slate-600">{col.valuationDate}</td>
                          <td className="px-4 py-3">
                            <Badge tone={col.status === 'PLEDGED' ? 'blue' : col.status === 'RELEASED' ? 'green' : 'red'}>
                              {col.status}
                            </Badge>
                          </td>
                          <td className="px-4 py-3 text-right">
                            {col.status === 'PLEDGED' && canReleaseCollateral ? (
                              <Button
                                variant="secondary"
                                onClick={() => setCollateralToRelease(col)}
                              >
                                Release
                              </Button>
                            ) : null}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}
          </Card>
        </div>
      ) : null}

      {/* Add Collateral Modal */}
      <Modal
        isOpen={isAddCollateralOpen}
        onClose={() => setIsAddCollateralOpen(false)}
        title="Add Pledged Collateral"
        description="Register a security asset pledged by the borrower."
        footer={
          <>
            <Button
              variant="secondary"
              type="button"
              onClick={() => setIsAddCollateralOpen(false)}
              disabled={addCollateralMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="add-collateral-form"
              disabled={isSubmitting || addCollateralMutation.isPending}
            >
              {isSubmitting || addCollateralMutation.isPending ? 'Pledging...' : 'Add Collateral'}
            </Button>
          </>
        }
      >
        <form
          id="add-collateral-form"
          onSubmit={handleSubmit((data) => addCollateralMutation.mutate(data))}
          className="space-y-4"
          noValidate
        >
          {addCollateralMutation.isError ? (
            <ErrorBox>{parseApiError(addCollateralMutation.error).message}</ErrorBox>
          ) : null}

          <Field label="Collateral Type">
            <select {...register('collateralType')} className={inputClass}>
              <option value="PROPERTY">PROPERTY (Real Estate)</option>
              <option value="VEHICLE">VEHICLE (Automobile)</option>
              <option value="FIXED_DEPOSIT">FIXED_DEPOSIT (Term Deposit)</option>
              <option value="SECURITIES">SECURITIES (Equities/Bonds)</option>
              <option value="OTHER">OTHER</option>
            </select>
          </Field>

          <Field label="Description">
            <input
              type="text"
              placeholder="e.g. 2022 Toyota RAV4 VIN #123456"
              {...register('description', { required: true })}
              className={inputClass}
            />
          </Field>

          <Field label="Estimated Valuation (USD)">
            <input
              type="text"
              placeholder="e.g. 28000.00"
              {...register('estimatedValue', { required: true })}
              className={inputClass}
            />
          </Field>

          <Field label="Valuation Date">
            <input
              type="date"
              {...register('valuationDate', { required: true })}
              className={inputClass}
            />
          </Field>
        </form>
      </Modal>

      {/* Release Collateral Confirm Modal */}
      <Modal
        isOpen={!!collateralToRelease}
        onClose={() => setCollateralToRelease(null)}
        title="Release Collateral"
        description="Confirm that this secured loan is fully satisfied and the collateral should be released back to the borrower."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setCollateralToRelease(null)}
              disabled={releaseMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              onClick={() => {
                if (collateralToRelease) {
                  releaseMutation.mutate(collateralToRelease.collateralId);
                }
              }}
              disabled={releaseMutation.isPending}
            >
              {releaseMutation.isPending ? 'Releasing...' : 'Confirm Release'}
            </Button>
          </>
        }
      >
        {collateralToRelease ? (
          <div className="space-y-2 text-sm text-slate-700">
            {releaseMutation.isError ? (
              <ErrorBox>{parseApiError(releaseMutation.error).message}</ErrorBox>
            ) : null}
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Asset:</span>
              <span className="font-semibold text-slate-900">{collateralToRelease.description}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Valuation:</span>
              <span className="font-semibold text-slate-900">{formatCents(collateralToRelease.estimatedValueCents)}</span>
            </div>
            <div className="flex justify-between pt-1">
              <span className="text-slate-500">Current Status:</span>
              <Badge tone="blue">{collateralToRelease.status}</Badge>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}
