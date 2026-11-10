'use client';

import { useEffect, useState } from 'react';
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
  Modal,
  PageHeader,
  SkeletonTable,
  createOutboundPayment,
  dollarsToCents,
  formatCents,
  formatDateTime,
  getCustomerAccounts,
  inputClass,
  listExternalPayments,
  maskUuid,
  parseApiError,
  useAuth,
  type ExternalPayment,
  type PaymentRail,
} from '@novo/shared';
import { externalPaymentSchema, type ExternalPaymentFormValues } from './schema';

export default function ExternalPaymentsPage() {
  const { customerId, accountId: currentAccountId } = useAuth();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<'new' | 'history'>('new');
  const [idempotencyKey, setIdempotencyKey] = useState<string>('');
  const [pendingValues, setPendingValues] = useState<ExternalPaymentFormValues | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  useEffect(() => {
    setIdempotencyKey(crypto.randomUUID());
  }, []);

  const accountsQuery = useQuery({
    queryKey: ['customer-accounts', customerId],
    queryFn: () => getCustomerAccounts(customerId!),
    enabled: !!customerId,
  });

  const historyQuery = useQuery({
    queryKey: ['external-payments-history'],
    queryFn: () => listExternalPayments(),
  });

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ExternalPaymentFormValues>({
    resolver: zodResolver(externalPaymentSchema),
    defaultValues: {
      rail: 'ACH',
      fromAccountId: currentAccountId || '',
      externalAccountNumber: '',
      externalRoutingNumber: '',
      externalBankName: '',
      amount: '',
    },
  });

  const selectedRail = watch('rail');

  const paymentMutation = useMutation({
    mutationFn: async (values: ExternalPaymentFormValues) => {
      const cents = dollarsToCents(values.amount);
      if (!cents) throw new Error('Invalid amount');

      return await createOutboundPayment({
        rail: values.rail,
        fromAccountId: values.fromAccountId,
        externalAccountNumber: values.externalAccountNumber,
        externalRoutingNumber: values.externalRoutingNumber,
        externalBankName: values.externalBankName,
        amountCents: cents,
        idempotencyKey,
      });
    },
    onSuccess: async (data) => {
      setPendingValues(null);
      setSuccessMessage(`Payment ${maskUuid(data.paymentId)} submitted successfully via ${data.rail}. Status: ${data.status}`);
      reset();
      setIdempotencyKey(crypto.randomUUID());
      await queryClient.invalidateQueries({ queryKey: ['external-payments-history'] });
      setActiveTab('history');
    },
  });

  const handleFormSubmit = (values: ExternalPaymentFormValues) => {
    const cents = dollarsToCents(values.amount);
    // If over $1,000 (100,000 cents), require confirmation modal
    if (cents && cents > 100_000) {
      setPendingValues(values);
    } else {
      paymentMutation.mutate(values);
    }
  };

  const handleReset = () => {
    reset();
    setIdempotencyKey(crypto.randomUUID());
    setSuccessMessage(null);
  };

  const getRailLimitDescription = (rail: PaymentRail) => {
    switch (rail) {
      case 'ACH':
        return 'Max $25,000.00 (Standard ACH processing 1-2 business days)';
      case 'RTGS':
        return 'Min $10,000.00, Max $1,000,000.00 (Real-time gross settlement)';
      case 'SWIFT_SIM':
        return 'Max $500,000.00 (Cross-border simulated wire transfer)';
      default:
        return '';
    }
  };

  const getStatusTone = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'green';
      case 'PROCESSING':
      case 'PENDING':
        return 'yellow';
      case 'FAILED':
      case 'RETURNED':
        return 'red';
      default:
        return 'gray';
    }
  };

  return (
    <>
      <PageHeader title="External Payments" />

      {successMessage ? (
        <div className="mb-6 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 flex justify-between items-center">
          <span>{successMessage}</span>
          <button
            type="button"
            onClick={() => setSuccessMessage(null)}
            className="text-emerald-600 hover:text-emerald-900"
          >
            &times;
          </button>
        </div>
      ) : null}

      <div className="mb-6 border-b border-slate-200">
        <nav className="flex space-x-6" aria-label="External Payment Tabs">
          <button
            type="button"
            onClick={() => setActiveTab('new')}
            className={`border-b-2 pb-3 text-sm font-medium transition ${
              activeTab === 'new'
                ? 'border-slate-900 text-slate-900 font-semibold'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            New Payment
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('history')}
            className={`border-b-2 pb-3 text-sm font-medium transition ${
              activeTab === 'history'
                ? 'border-slate-900 text-slate-900 font-semibold'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            Payment History
            {historyQuery.data?.length ? (
              <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                {historyQuery.data.length}
              </span>
            ) : null}
          </button>
        </nav>
      </div>

      {activeTab === 'new' ? (
        <div className="mx-auto max-w-2xl">
          <Card title="Initiate External Transfer">
            <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-5" noValidate>
              {paymentMutation.isError ? (
                <ErrorBox>{parseApiError(paymentMutation.error).message}</ErrorBox>
              ) : null}

              <Field label="Payment Rail" error={errors.rail?.message}>
                <div className="grid grid-cols-3 gap-3">
                  {(['ACH', 'RTGS', 'SWIFT_SIM'] as const).map((r) => (
                    <label
                      key={r}
                      className={`flex cursor-pointer flex-col items-center justify-center rounded-lg border p-3 text-center transition ${
                        selectedRail === r
                          ? 'border-slate-900 bg-slate-50 text-slate-900 font-semibold shadow-sm'
                          : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                      }`}
                    >
                      <input
                        type="radio"
                        value={r}
                        {...register('rail')}
                        className="sr-only"
                      />
                      <span className="text-sm">{r === 'SWIFT_SIM' ? 'SWIFT' : r}</span>
                    </label>
                  ))}
                </div>
                <p className="mt-1 text-xs text-slate-500">{getRailLimitDescription(selectedRail)}</p>
              </Field>

              <Field label="From Account" error={errors.fromAccountId?.message}>
                <select {...register('fromAccountId')} className={inputClass}>
                  <option value="">Select source account...</option>
                  {accountsQuery.data?.map((acc) => (
                    <option key={acc.id} value={acc.id}>
                      {acc.accountType.toUpperCase()} — {acc.accountNumber} ({formatCents(acc.balanceCents)})
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="Destination Bank Name" error={errors.externalBankName?.message}>
                <input
                  type="text"
                  placeholder="e.g. JPMorgan Chase"
                  {...register('externalBankName')}
                  className={inputClass}
                />
              </Field>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field label="Routing / Sort Code" error={errors.externalRoutingNumber?.message}>
                  <input
                    type="text"
                    placeholder="9-digit routing"
                    {...register('externalRoutingNumber')}
                    className={inputClass}
                  />
                </Field>
                <Field label="Account Number / IBAN" error={errors.externalAccountNumber?.message}>
                  <input
                    type="text"
                    placeholder="Recipient account"
                    {...register('externalAccountNumber')}
                    className={inputClass}
                  />
                </Field>
              </div>

              <Field label="Transfer Amount (USD)" error={errors.amount?.message}>
                <div className="relative">
                  <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                    $
                  </span>
                  <input
                    type="text"
                    placeholder="150.00"
                    {...register('amount')}
                    className={`${inputClass} pl-7`}
                  />
                </div>
              </Field>

              <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-500 flex items-center justify-between">
                <span>Idempotency Key: <span className="font-mono text-slate-700">{maskUuid(idempotencyKey)}</span></span>
                <span className="text-slate-400">Protects against double debits</span>
              </div>

              <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
                <Button type="button" variant="secondary" onClick={handleReset}>
                  Reset
                </Button>
                <Button type="submit" disabled={isSubmitting || paymentMutation.isPending}>
                  {isSubmitting || paymentMutation.isPending ? 'Sending...' : 'Send Payment'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      ) : (
        <div>
          {historyQuery.isLoading ? <SkeletonTable rows={4} cols={6} /> : null}

          {historyQuery.isError ? (
            <ErrorBox>{parseApiError(historyQuery.error).message}</ErrorBox>
          ) : null}

          {historyQuery.data ? (
            historyQuery.data.length === 0 ? (
              <EmptyState
                title="No external payments recorded"
                description="You haven't initiated or received any external transfers yet."
                action={
                  <Button onClick={() => setActiveTab('new')}>Send an external payment</Button>
                }
              />
            ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
                <table className="w-full text-left text-sm" aria-label="External Payments History">
                  <caption className="sr-only">List of your external payments</caption>
                  <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                    <tr>
                      <th scope="col" className="px-4 py-3">Direction</th>
                      <th scope="col" className="px-4 py-3">Rail</th>
                      <th scope="col" className="px-4 py-3">Recipient / Bank</th>
                      <th scope="col" className="px-4 py-3">Amount</th>
                      <th scope="col" className="px-4 py-3">Status</th>
                      <th scope="col" className="px-4 py-3">Date</th>
                      <th scope="col" className="px-4 py-3">Ref ID</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {historyQuery.data.map((p) => {
                      const isOutbound = p.direction === 'OUTBOUND';
                      return (
                        <tr key={p.paymentId} className="hover:bg-slate-50">
                          <td className="px-4 py-3">
                            <span
                              className={`inline-flex items-center gap-1 font-semibold text-xs ${
                                isOutbound ? 'text-slate-700' : 'text-emerald-700'
                              }`}
                            >
                              {isOutbound ? (
                                <>
                                  <span aria-hidden="true">&uarr;</span> OUTBOUND
                                </>
                              ) : (
                                <>
                                  <span aria-hidden="true">&darr;</span> INBOUND
                                </>
                              )}
                            </span>
                          </td>
                          <td className="px-4 py-3 font-semibold text-slate-800">{p.rail}</td>
                          <td className="px-4 py-3">
                            <div className="font-medium text-slate-900">{p.externalBankName || '—'}</div>
                            <div className="font-mono text-xs text-slate-500">{p.externalAccountNumber || ''}</div>
                          </td>
                          <td className="px-4 py-3 font-bold text-slate-900">{formatCents(p.amountCents)}</td>
                          <td className="px-4 py-3">
                            <Badge tone={getStatusTone(p.status)}>{p.status}</Badge>
                          </td>
                          <td className="px-4 py-3 text-slate-500">{formatDateTime(p.createdAt)}</td>
                          <td className="px-4 py-3 font-mono text-xs text-slate-400">{maskUuid(p.paymentId)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )
          ) : null}
        </div>
      )}

      {/* Confirmation Modal for Payments > $1,000 */}
      <Modal
        isOpen={!!pendingValues}
        onClose={() => setPendingValues(null)}
        title="Confirm High-Value Transfer"
        description="This transfer exceeds $1,000.00. Please review the destination and amount."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => setPendingValues(null)}
              disabled={paymentMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              onClick={() => {
                if (pendingValues) {
                  paymentMutation.mutate(pendingValues);
                }
              }}
              disabled={paymentMutation.isPending}
            >
              {paymentMutation.isPending ? 'Sending...' : 'Authorize & Send'}
            </Button>
          </>
        }
      >
        {pendingValues ? (
          <div className="space-y-3 text-sm text-slate-700">
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Rail:</span>
              <span className="font-semibold text-slate-900">{pendingValues.rail}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Recipient Bank:</span>
              <span className="text-slate-900">{pendingValues.externalBankName}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Account Number:</span>
              <span className="font-mono text-slate-900">{pendingValues.externalAccountNumber}</span>
            </div>
            <div className="flex justify-between border-b pb-2">
              <span className="text-slate-500">Routing Number:</span>
              <span className="font-mono text-slate-900">{pendingValues.externalRoutingNumber}</span>
            </div>
            <div className="flex justify-between pt-1 text-base font-bold text-slate-900">
              <span>Transfer Amount:</span>
              <span>{formatCents(dollarsToCents(pendingValues.amount))}</span>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}

