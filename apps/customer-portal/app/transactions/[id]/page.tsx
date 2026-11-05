'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { Badge, Card, ErrorBox, PageHeader, Spinner, formatCents, formatDateTime, getTransfer, maskUuid, parseApiError } from '@novo/shared';

export default function TransactionPage({ params }: { params: { id: string } }) {
  const transfer = useQuery({ queryKey: ['transfer', params.id], queryFn: () => getTransfer(params.id) });

  return (
    <>
      <PageHeader title="Transaction" actions={<Link href="/dashboard" className="text-sm text-sky-700 hover:underline">Back to dashboard</Link>} />
      {transfer.isLoading ? <Spinner /> : null}
      {transfer.isError ? (
        <ErrorBox>{parseApiError(transfer.error).status === 403 ? 'You do not have access to this transaction.' : parseApiError(transfer.error).message}</ErrorBox>
      ) : null}
      {transfer.data ? (
        <Card className="max-w-xl">
          <dl className="grid grid-cols-3 gap-y-3 text-sm">
            <dt className="text-slate-500">Transaction</dt>
            <dd className="col-span-2 font-mono">{transfer.data.transactionId}</dd>
            <dt className="text-slate-500">Status</dt>
            <dd className="col-span-2">
              <Badge tone={transfer.data.status === 'completed' ? 'green' : 'yellow'}>{transfer.data.status}</Badge>
            </dd>
            <dt className="text-slate-500">Amount</dt>
            <dd className="col-span-2 font-medium">{formatCents(transfer.data.amountCents)}</dd>
            {transfer.data.fromAccount ? (
              <>
                <dt className="text-slate-500">From</dt>
                <dd className="col-span-2 font-mono">{maskUuid(transfer.data.fromAccount)}</dd>
              </>
            ) : null}
            {transfer.data.toAccount ? (
              <>
                <dt className="text-slate-500">To</dt>
                <dd className="col-span-2 font-mono">{maskUuid(transfer.data.toAccount)}</dd>
              </>
            ) : null}
            <dt className="text-slate-500">Time</dt>
            <dd className="col-span-2">{formatDateTime(transfer.data.createdAt)}</dd>
          </dl>
        </Card>
      ) : null}
    </>
  );
}
