'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createClient } from '@supabase/supabase-js';
import {
  Badge,
  Button,
  EmptyState,
  ErrorBox,
  Field,
  Modal,
  PageHeader,
  SkeletonTable,
  formatCents,
  getOverdueRepayments,
  inputClass,
  maskUuid,
  parseApiError,
  recordCollectionAction,
  type OverdueRepayment,
} from '@novo/shared';
import { collectionActionSchema, type CollectionActionFormValues } from './schema';

export default function CollectionsPage() {
  const queryClient = useQueryClient();
  const [selectedRepayment, setSelectedRepayment] = useState<OverdueRepayment | null>(null);

  const overdueQuery = useQuery({
    queryKey: ['overdue-repayments'],
    queryFn: getOverdueRepayments,
  });

  // Supabase Realtime subscription on loan_repayments UPDATE where status=OVERDUE
  useEffect(() => {
    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
    if (!supabaseUrl || !supabaseKey) return;

    const supabase = createClient(supabaseUrl, supabaseKey);
    const channel = supabase
      .channel('loan_repayments_collections')
      .on(
        'postgres_changes',
        {
          event: 'UPDATE',
          schema: 'public',
          table: 'loan_repayments',
          filter: 'status=eq.OVERDUE',
        },
        () => {
          void queryClient.invalidateQueries({ queryKey: ['overdue-repayments'] });
        }
      )
      .subscribe();

    return () => {
      void supabase.removeChannel(channel);
    };
  }, [queryClient]);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CollectionActionFormValues>({
    resolver: zodResolver(collectionActionSchema),
    defaultValues: {
      actionType: 'REMINDER_SENT',
      notes: '',
      typedConfirmation: '',
    },
  });

  const selectedActionType = watch('actionType');
  const typedConfirmation = watch('typedConfirmation');

  const actionMutation = useMutation({
    mutationFn: async (values: CollectionActionFormValues) => {
      if (!selectedRepayment) throw new Error('No repayment selected');

      return await recordCollectionAction(selectedRepayment.repaymentId, {
        actionType: values.actionType,
        notes: values.notes,
      });
    },
    onSuccess: async () => {
      setSelectedRepayment(null);
      reset();
      await queryClient.invalidateQueries({ queryKey: ['overdue-repayments'] });
    },
  });

  const openActionModal = (item: OverdueRepayment) => {
    setSelectedRepayment(item);
    reset({
      actionType: 'REMINDER_SENT',
      notes: '',
      typedConfirmation: '',
    });
  };

  const onSubmitAction = (values: CollectionActionFormValues) => {
    actionMutation.mutate(values);
  };

  const isWriteOff = selectedActionType === 'WRITE_OFF_APPROVED';

  return (
    <>
      <PageHeader title="Collections & Delinquency Management" />

      {overdueQuery.isLoading ? <SkeletonTable rows={5} cols={7} /> : null}

      {overdueQuery.isError ? (
        <ErrorBox>{parseApiError(overdueQuery.error).message}</ErrorBox>
      ) : null}

      {overdueQuery.data ? (
        overdueQuery.data.length === 0 ? (
          <EmptyState
            title="No overdue repayments"
            description="All active loan accounts are current. There are no delinquent installments requiring collection."
          />
        ) : (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm" aria-label="Overdue Repayments List">
              <caption className="sr-only">Delinquent loan repayments requiring action</caption>
              <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                <tr>
                  <th scope="col" className="px-4 py-3">Loan ID</th>
                  <th scope="col" className="px-4 py-3">Customer ID</th>
                  <th scope="col" className="px-4 py-3">Due Date</th>
                  <th scope="col" className="px-4 py-3">Days Overdue</th>
                  <th scope="col" className="px-4 py-3">Total Due</th>
                  <th scope="col" className="px-4 py-3">Last Action</th>
                  <th scope="col" className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {overdueQuery.data.map((item) => (
                  <tr key={item.repaymentId} className="hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-slate-700">
                      <Link href={`/loans/${item.applicationId}`} className="text-sky-600 hover:underline">
                        {maskUuid(item.applicationId)}
                      </Link>
                    </td>
                    <td className="px-4 py-3 font-mono text-slate-700">{maskUuid(item.customerId)}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{item.dueDate}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`font-semibold ${
                          item.daysOverdue > 90
                            ? 'text-rose-600 font-bold'
                            : item.daysOverdue > 30
                            ? 'text-amber-600'
                            : 'text-slate-700'
                        }`}
                      >
                        {item.daysOverdue} days
                      </span>
                    </td>
                    <td className="px-4 py-3 font-bold text-slate-900">{formatCents(item.totalDueCents)}</td>
                    <td className="px-4 py-3">
                      {item.lastActionType ? (
                        <Badge tone="gray">{item.lastActionType}</Badge>
                      ) : (
                        <span className="text-xs text-slate-400">None</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button onClick={() => openActionModal(item)}>Record Action</Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      {/* Record Action Modal */}
      <Modal
        isOpen={!!selectedRepayment}
        onClose={() => setSelectedRepayment(null)}
        title="Record Collection Action"
        description="Log an intervention or legal/recovery workflow for this delinquent repayment."
        footer={
          <>
            <Button
              variant="secondary"
              type="button"
              onClick={() => setSelectedRepayment(null)}
              disabled={actionMutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="collection-action-form"
              variant={isWriteOff ? 'danger' : 'primary'}
              disabled={
                isSubmitting ||
                actionMutation.isPending ||
                (isWriteOff && typedConfirmation !== 'WRITE OFF')
              }
            >
              {actionMutation.isPending ? 'Logging...' : 'Record Action'}
            </Button>
          </>
        }
      >
        {selectedRepayment ? (
          <form
            id="collection-action-form"
            onSubmit={handleSubmit(onSubmitAction)}
            className="space-y-4"
            noValidate
          >
            {actionMutation.isError ? (
              <ErrorBox>{parseApiError(actionMutation.error).message}</ErrorBox>
            ) : null}

            <div className="rounded-md bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
              <div className="flex justify-between">
                <span>Application:</span>
                <span className="font-mono text-slate-900">{maskUuid(selectedRepayment.applicationId)}</span>
              </div>
              <div className="flex justify-between">
                <span>Days Delinquent:</span>
                <span className="font-bold text-rose-600">{selectedRepayment.daysOverdue} days</span>
              </div>
              <div className="flex justify-between">
                <span>Overdue Amount:</span>
                <span className="font-bold text-slate-900">{formatCents(selectedRepayment.totalDueCents)}</span>
              </div>
            </div>

            <Field label="Action Type" error={errors.actionType?.message}>
              <select {...register('actionType')} className={inputClass}>
                <option value="REMINDER_SENT">REMINDER_SENT (Automated SMS/Email)</option>
                <option value="PHONE_CALL">PHONE_CALL (Agent Telephone Contact)</option>
                <option value="DEMAND_LETTER">DEMAND_LETTER (Formal Written Demand)</option>
                <option value="LEGAL_NOTICE">LEGAL_NOTICE (Pre-Litigation Notice)</option>
                <option value="WRITE_OFF_PROPOSED">WRITE_OFF_PROPOSED (Recommend Write-Off)</option>
                <option value="WRITE_OFF_APPROVED">WRITE_OFF_APPROVED (Final Bad Debt Write-Off)</option>
              </select>
            </Field>

            <Field label="Action Notes & Summary" error={errors.notes?.message}>
              <textarea
                rows={3}
                placeholder="Details of conversation, borrower response, or recovery justification..."
                {...register('notes')}
                className={inputClass}
              />
            </Field>

            {isWriteOff ? (
              <div className="rounded-md border border-rose-200 bg-rose-50 p-4 space-y-3">
                <div className="text-sm font-semibold text-rose-900">
                  Critical Action: Write-Off Approval
                </div>
                <p className="text-xs text-rose-700">
                  Approving a write-off writes down this debt to zero on the ledger and moves the loan to loss recovery.
                  To confirm, type <span className="font-mono font-bold">WRITE OFF</span> below:
                </p>
                <Field label="Type 'WRITE OFF' to confirm" error={errors.typedConfirmation?.message}>
                  <input
                    type="text"
                    placeholder="WRITE OFF"
                    {...register('typedConfirmation')}
                    className={`${inputClass} border-rose-300 font-mono`}
                  />
                </Field>
              </div>
            ) : null}
          </form>
        ) : null}
      </Modal>
    </>
  );
}

