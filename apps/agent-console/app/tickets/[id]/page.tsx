'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  addMessage,
  formatDateTime,
  getCustomer,
  getTicket,
  inputClass,
  kycTone,
  maskEmail,
  maskMobile,
  maskUuid,
  parseApiError,
  priorityTone,
  statusTone,
  updateTicket,
  useAuth,
  type TicketStatus,
} from '@novo/shared';

const ORDER: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED'];
/** Only the next step is selectable: no skipping, no reversing (the backend enforces the same rule with a 422). */
const nextStatus = (current: TicketStatus): TicketStatus | null => ORDER[ORDER.indexOf(current) + 1] ?? null;

export default function AgentTicketPage({ params }: { params: { id: string } }) {
  const { agentId } = useAuth();
  const queryClient = useQueryClient();
  const [body, setBody] = useState('');
  const [internal, setInternal] = useState(false);
  const [target, setTarget] = useState<TicketStatus | ''>('');

  const ticket = useQuery({ queryKey: ['ticket', params.id], queryFn: () => getTicket(params.id) });
  const customerId = ticket.data?.customerId;
  const customer = useQuery({ queryKey: ['customer', customerId], queryFn: () => getCustomer(customerId!), enabled: !!customerId });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['ticket', params.id] });

  const reply = useMutation({
    mutationFn: () => addMessage(params.id, { messageBody: body.trim(), isInternalNote: internal }),
    onSuccess: async () => {
      setBody('');
      await refresh();
    },
  });
  const transition = useMutation({
    mutationFn: (status: TicketStatus) => updateTicket(params.id, { status }),
    onSuccess: async () => {
      setTarget('');
      await refresh();
    },
  });
  const assign = useMutation({
    mutationFn: () => updateTicket(params.id, { assignedAgentId: agentId! }),
    onSuccess: refresh,
  });

  const messages = [...(ticket.data?.messages ?? [])].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const allowed = ticket.data ? nextStatus(ticket.data.status) : null;

  return (
    <>
      <PageHeader title="Ticket" actions={<Link href="/tickets" className="text-sm text-sky-700 hover:underline">All tickets</Link>} />
      {ticket.isLoading ? <Spinner /> : null}
      {ticket.isError ? <ErrorBox>{parseApiError(ticket.error).message}</ErrorBox> : null}
      {ticket.data ? (
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Left: customer + ticket facts */}
          <div className="space-y-6 lg:col-span-1">
            <Card title="Customer">
              {customer.isLoading ? <Spinner /> : null}
              {customer.isError ? <ErrorBox>{parseApiError(customer.error).message}</ErrorBox> : null}
              {customer.data ? (
                <dl className="grid grid-cols-2 gap-y-2 text-sm">
                  <dt className="text-slate-500">Name</dt>
                  <dd>{customer.data.displayName}</dd>
                  <dt className="text-slate-500">Customer no.</dt>
                  <dd className="font-mono">{customer.data.customerNumber}</dd>
                  <dt className="text-slate-500">ID</dt>
                  <dd className="font-mono">{maskUuid(customer.data.id)}</dd>
                  <dt className="text-slate-500">Mobile</dt>
                  <dd className="font-mono">{maskMobile(customer.data.mobileNo)}</dd>
                  <dt className="text-slate-500">Email</dt>
                  <dd className="font-mono">{maskEmail(customer.data.email) || '—'}</dd>
                  <dt className="text-slate-500">KYC</dt>
                  <dd>
                    <Badge tone={kycTone(customer.data.kycStatus)}>{customer.data.kycStatus.toUpperCase()}</Badge>
                  </dd>
                  <dt className="text-slate-500">Risk tier</dt>
                  <dd>
                    <Badge tone={customer.data.amlRiskRating === 'high' ? 'red' : customer.data.amlRiskRating === 'medium' ? 'yellow' : 'green'}>
                      {customer.data.amlRiskRating.toUpperCase()}
                    </Badge>
                  </dd>
                </dl>
              ) : null}
            </Card>

            <Card title="Ticket">
              <h2 className="font-semibold">{ticket.data.subject}</h2>
              <div className="mt-2 flex flex-wrap gap-2">
                <Badge tone={priorityTone(ticket.data.priority)}>{ticket.data.priority}</Badge>
                <Badge tone={statusTone(ticket.data.status)}>{ticket.data.status}</Badge>
              </div>
              <p className="mt-2 text-sm text-slate-500">
                {ticket.data.category.replace('_', ' ')} · opened {formatDateTime(ticket.data.createdAt)}
              </p>
              {ticket.data.relatedTransactionId ? (
                <p className="mt-1 text-sm">
                  Transaction <span className="font-mono">{maskUuid(ticket.data.relatedTransactionId)}</span>
                </p>
              ) : null}
              <p className="mt-1 text-sm">
                Assigned: <span className="font-mono">{ticket.data.assignedAgentId ? maskUuid(ticket.data.assignedAgentId) : 'nobody'}</span>
              </p>
              {ticket.data.assignedAgentId !== agentId ? (
                <Button variant="secondary" className="mt-3" onClick={() => assign.mutate()} disabled={assign.isPending || !agentId}>
                  Assign to me
                </Button>
              ) : null}

              <div className="mt-5 border-t border-slate-100 pt-4">
                <label className="text-sm font-medium text-slate-700" htmlFor="status">
                  Change status
                </label>
                <div className="mt-1 flex gap-2">
                  <select id="status" className={inputClass} value={target} onChange={(e) => setTarget(e.target.value as TicketStatus | '')}>
                    <option value="">{allowed ? 'Select next status' : 'No further status'}</option>
                    {ORDER.map((s) => (
                      // only the single valid next state is enabled
                      <option key={s} value={s} disabled={s !== allowed}>
                        {s}
                      </option>
                    ))}
                  </select>
                  <Button onClick={() => target && transition.mutate(target)} disabled={!target || transition.isPending}>
                    Apply
                  </Button>
                </div>
                {transition.isError ? <div className="mt-2"><ErrorBox>{parseApiError(transition.error).message}</ErrorBox></div> : null}
              </div>
            </Card>
          </div>

          {/* Right: thread */}
          <div className="space-y-6 lg:col-span-2">
            <Card title="Messages">
              <ul className="space-y-3">
                {messages.map((m) => (
                  <li
                    key={m.messageId}
                    className={`rounded-md p-3 text-sm ${
                      m.isInternalNote
                        ? 'border border-amber-300 bg-amber-100'
                        : m.senderType === 'CUSTOMER'
                          ? 'bg-sky-50'
                          : m.senderType === 'AGENT'
                            ? 'bg-slate-100'
                            : 'bg-white ring-1 ring-slate-200'
                    }`}
                  >
                    <div className="mb-1 flex items-center gap-2 text-xs font-medium text-slate-600">
                      {m.isInternalNote ? <span className="rounded bg-amber-500 px-1.5 py-0.5 text-[10px] font-bold uppercase text-white">Internal</span> : null}
                      <span>{m.senderType}</span>
                      <span className="text-slate-400">· {formatDateTime(m.createdAt)}</span>
                    </div>
                    <p className="whitespace-pre-wrap">{m.messageBody}</p>
                  </li>
                ))}
              </ul>
            </Card>

            <Card title="Reply">
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (body.trim()) reply.mutate();
                }}
                className="space-y-3"
              >
                {reply.isError ? <ErrorBox>{parseApiError(reply.error).message}</ErrorBox> : null}
                <textarea
                  value={body}
                  onChange={(e) => setBody(e.target.value)}
                  rows={4}
                  maxLength={5000}
                  className={`${inputClass} ${internal ? 'border-amber-400 bg-amber-50' : ''}`}
                  placeholder={internal ? 'Internal note (the customer will not see this)…' : 'Reply to the customer…'}
                />
                <div className="flex items-center justify-between">
                  <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={internal} onChange={(e) => setInternal(e.target.checked)} />
                    Internal note
                  </label>
                  <Button type="submit" disabled={reply.isPending || !body.trim()}>
                    {reply.isPending ? 'Sending…' : internal ? 'Add internal note' : 'Send reply'}
                  </Button>
                </div>
              </form>
            </Card>
          </div>
        </div>
      ) : null}
    </>
  );
}
