'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, Card, ErrorBox, Field, PageHeader, createTicket, inputClass, parseApiError } from '@novo/shared';

const schema = z.object({
  category: z.enum(['TRANSACTION_DISPUTE', 'ACCOUNT_LOCK', 'KYC_VERIFICATION', 'GENERAL_INQUIRY']),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  subject: z.string().trim().min(1, 'Required').max(200, 'Max 200 characters'),
  relatedTransactionId: z.string().trim().uuid('Must be a transaction ID (UUID)').optional().or(z.literal('')),
});
type FormValues = z.infer<typeof schema>;

export default function NewTicketPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { category: 'GENERAL_INQUIRY', priority: 'MEDIUM', subject: '', relatedTransactionId: '' },
  });

  const mutation = useMutation({
    mutationFn: createTicket,
    onSuccess: async (ticket) => {
      await queryClient.invalidateQueries({ queryKey: ['my-tickets'] });
      router.push(`/support/${ticket.ticketId}`);
    },
  });

  function onSubmit(values: FormValues) {
    mutation.mutate({
      category: values.category,
      priority: values.priority,
      subject: values.subject,
      ...(values.relatedTransactionId ? { relatedTransactionId: values.relatedTransactionId } : {}),
    });
  }

  return (
    <>
      <PageHeader title="New support ticket" />
      <Card className="max-w-xl">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          {mutation.isError ? <ErrorBox>{parseApiError(mutation.error).message}</ErrorBox> : null}
          <Field label="Category" error={errors.category?.message}>
            <select {...register('category')} className={inputClass}>
              <option value="TRANSACTION_DISPUTE">Transaction dispute</option>
              <option value="ACCOUNT_LOCK">Account lock</option>
              <option value="KYC_VERIFICATION">KYC verification</option>
              <option value="GENERAL_INQUIRY">General inquiry</option>
            </select>
          </Field>
          <Field label="Priority" error={errors.priority?.message}>
            <select {...register('priority')} className={inputClass}>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>
          </Field>
          <Field label="Subject" error={errors.subject?.message}>
            <input {...register('subject')} className={inputClass} maxLength={200} />
          </Field>
          <Field label="Related transaction ID (optional)" error={errors.relatedTransactionId?.message}>
            <input {...register('relatedTransactionId')} className={inputClass} placeholder="Transaction UUID" />
          </Field>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Submitting…' : 'Submit ticket'}
          </Button>
        </form>
      </Card>
    </>
  );
}
