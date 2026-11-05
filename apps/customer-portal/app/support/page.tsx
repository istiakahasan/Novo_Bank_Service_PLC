'use client';

import { keepPreviousData, useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  PageHeader,
  Pagination,
  Spinner,
  formatDateTime,
  listTickets,
  maskUuid,
  parseApiError,
  priorityTone,
  statusTone,
} from '@novo/shared';

export default function SupportPage() {
  const [page, setPage] = useState(0);
  const tickets = useQuery({ queryKey: ['my-tickets', page], queryFn: () => listTickets({ page, size: 20 }), placeholderData: keepPreviousData });

  return (
    <>
      <PageHeader
        title="Support"
        actions={
          <Link href="/support/new">
            <Button>New Ticket</Button>
          </Link>
        }
      />
      <Card>
        {tickets.isLoading ? <Spinner /> : null}
        {tickets.isError ? <ErrorBox>{parseApiError(tickets.error).message}</ErrorBox> : null}
        {tickets.data && tickets.data.content.length === 0 ? <p className="text-sm text-slate-500">You have no tickets.</p> : null}
        {tickets.data && tickets.data.content.length > 0 ? (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500">
                <th className="py-2">Ticket</th>
                <th>Subject</th>
                <th>Category</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {tickets.data.content.map((ticket) => (
                <tr key={ticket.ticketId} className="border-t border-slate-100">
                  <td className="py-2 font-mono">
                    <Link href={`/support/${ticket.ticketId}`} className="text-sky-700 hover:underline">
                      {maskUuid(ticket.ticketId)}
                    </Link>
                  </td>
                  <td>{ticket.subject}</td>
                  <td>{ticket.category.replace('_', ' ')}</td>
                  <td>
                    <Badge tone={priorityTone(ticket.priority)}>{ticket.priority}</Badge>
                  </td>
                  <td>
                    <Badge tone={statusTone(ticket.status)}>{ticket.status}</Badge>
                  </td>
                  <td>{formatDateTime(ticket.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
        {tickets.data ? <Pagination page={page} totalPages={tickets.data.totalPages} onChange={setPage} /> : null}
      </Card>
    </>
  );
}
