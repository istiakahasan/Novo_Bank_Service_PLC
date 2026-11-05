'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  addMessage,
  formatDateTime,
  getTicket,
  inputClass,
  parseApiError,
  priorityTone,
  statusTone,
} from '@novo/shared';

const schema = z.object({
  messageBody: z.string().trim().min(1, 'Write a message').max(5000, 'Max 5000 characters'),
});
type FormValues = z.infer<typeof schema>;

export default function TicketDetailPage({ params }: { params: { id: string } }) {
  const queryClient = useQueryClient();
  const ticket = useQuery({ queryKey: ['ticket', params.id], queryFn: () => getTicket(params.id) });
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { messageBody: '' } });

  const reply = useMutation({
    mutationFn: (values: FormValues) => addMessage(params.id, { messageBody: values.messageBody, isInternalNote: false }),
    onSuccess: async () => {
      reset();
      await queryClient.invalidateQueries({ queryKey: ['ticket', params.id] });
    },
  });

  // Internal notes are never shown to customers (the API already omits them; this is a second filter).
  const messages = (ticket.data?.messages ?? []).filter((m) => !m.isInternalNote);
  const closed = ticket.data?.status === 'CLOSED';

  return (
    <>
      <PageHeader title="Ticket" actions={<Link href="/support" className="text-sm text-sky-700 hover:underline">All tickets</Link>} />
      {ticket.isLoading ? <Spinner /> : null}
      {ticket.isError ? (
        <ErrorBox>{parseApiError(ticket.error).status === 403 ? 'This ticket belongs to another customer.' : parseApiError(ticket.error).message}</ErrorBox>
      ) : null}
      {ticket.data ? (
        <div className="space-y-6">
          <Card>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-lg font-semibold">{ticket.data.subject}</h2>
              <Badge tone={priorityTone(ticket.data.priority)}>{ticket.data.priority}</Badge>
              <Badge tone={statusTone(ticket.data.status)}>{ticket.data.status}</Badge>
            </div>
            <p className="mt-1 text-sm text-slate-500">
              {ticket.data.category.replace('_', ' ')} · opened {formatDateTime(ticket.data.createdAt)}
            </p>
          </Card>

          <Card title="Messages">
            <ul className="space-y-3">
              {messages.map((m) => (
                <li
                  key={m.messageId}
                  className={`rounded-md p-3 text-sm ${m.senderType === 'CUSTOMER' ? 'ml-8 bg-sky-50' : m.senderType === 'AGENT' ? 'mr-8 bg-slate-100' : 'bg-white text-slate-500 ring-1 ring-slate-200'}`}
                >
                  <div className="mb-1 text-xs font-medium text-slate-500">
                    {m.senderType === 'CUSTOMER' ? 'You' : m.senderType === 'AGENT' ? 'Support agent' : 'System'} · {formatDateTime(m.createdAt)}
                  </div>
                  <p className="whitespace-pre-wrap">{m.messageBody}</p>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="Reply">
            {closed ? (
              <p className="text-sm text-slate-500">This ticket is closed.</p>
            ) : (
              <form onSubmit={handleSubmit((values) => reply.mutate(values))} className="space-y-3" noValidate>
                {reply.isError ? <ErrorBox>{parseApiError(reply.error).message}</ErrorBox> : null}
                {/* hidden field: customers can never post internal notes */}
                <input type="hidden" name="isInternalNote" value="false" readOnly />
                <textarea {...register('messageBody')} rows={4} className={inputClass} placeholder="Write your reply…" />
                {errors.messageBody ? <p className="text-xs text-rose-600">{errors.messageBody.message}</p> : null}
                <Button type="submit" disabled={reply.isPending}>
                  {reply.isPending ? 'Sending…' : 'Send reply'}
                </Button>
              </form>
            )}
          </Card>
        </div>
      ) : null}
    </>
  );
}
