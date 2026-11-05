'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import {
  Badge,
  Card,
  ErrorBox,
  PageHeader,
  Pagination,
  Spinner,
  formatDateTime,
  inputClass,
  listTickets,
  maskUuid,
  parseApiError,
  priorityTone,
  statusTone,
  type TicketPriority,
  type TicketStatus,
} from '@novo/shared';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function TicketsPage() {
  const [status, setStatus] = useState<TicketStatus | ''>('');
  const [priority, setPriority] = useState<TicketPriority | ''>('');
  const [agent, setAgent] = useState('');
  const [page, setPage] = useState(0);
  const agentValid = agent === '' || UUID.test(agent);

  const tickets = useQuery({
    queryKey: ['tickets', status, priority, agent, page],
    queryFn: () => listTickets({ status, priority, assignedAgentId: agent || undefined, page, size: 20 }),
    placeholderData: keepPreviousData,
    enabled: agentValid,
  });

  function change<T>(setter: (v: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <>
      <PageHeader title="Tickets" />
      <Card className="mb-4">
        <div className="grid gap-3 md:grid-cols-3">
          <label className="text-sm">
            <span className="mb-1 block text-slate-600">Status</span>
            <select className={inputClass} value={status} onChange={(e) => change(setStatus)(e.target.value as TicketStatus | '')}>
              <option value="">All</option>
              {['OPEN', 'IN_PROGRESS', 'PENDING_CUSTOMER', 'RESOLVED', 'CLOSED'].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-slate-600">Priority</span>
            <select className={inputClass} value={priority} onChange={(e) => change(setPriority)(e.target.value as TicketPriority | '')}>
              <option value="">All</option>
              {['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map((p) => (
                <option key={p}>{p}</option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="mb-1 block text-slate-600">Assigned agent ID</span>
            <input className={inputClass} value={agent} onChange={(e) => change(setAgent)(e.target.value.trim())} placeholder="agent UUID" />
            {!agentValid ? <span className="text-xs text-rose-600">Must be a UUID</span> : null}
          </label>
        </div>
      </Card>

      <Card>
        {tickets.isLoading ? <Spinner /> : null}
        {tickets.isError ? <ErrorBox>{parseApiError(tickets.error).message}</ErrorBox> : null}
        {tickets.data && tickets.data.content.length === 0 ? <p className="text-sm text-slate-500">No tickets match.</p> : null}
        {tickets.data && tickets.data.content.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500">
                  <th className="py-2">Ticket</th>
                  <th>Customer</th>
                  <th>Category</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Assigned agent</th>
                </tr>
              </thead>
              <tbody>
                {tickets.data.content.map((ticket) => (
                  <tr key={ticket.ticketId} className="border-t border-slate-100">
                    <td className="py-2 font-mono">
                      <Link href={`/tickets/${ticket.ticketId}`} className="text-sky-700 hover:underline">
                        {maskUuid(ticket.ticketId)}
                      </Link>
                    </td>
                    <td className="font-mono">{maskUuid(ticket.customerId)}</td>
                    <td>{ticket.category.replace('_', ' ')}</td>
                    <td>
                      <Badge tone={priorityTone(ticket.priority)}>{ticket.priority}</Badge>
                    </td>
                    <td>
                      <Badge tone={statusTone(ticket.status)}>{ticket.status}</Badge>
                    </td>
                    <td>{formatDateTime(ticket.createdAt)}</td>
                    <td className="font-mono">{ticket.assignedAgentId ? maskUuid(ticket.assignedAgentId) : <span className="text-slate-400">Unassigned</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {tickets.data ? <Pagination page={page} totalPages={tickets.data.totalPages} onChange={setPage} /> : null}
      </Card>
    </>
  );
}
