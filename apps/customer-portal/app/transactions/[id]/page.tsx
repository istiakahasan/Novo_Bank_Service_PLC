'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { createClient } from '@supabase/supabase-js';
import {
  Badge,
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  formatCents,
  formatDateTime,
  getTransfer,
  maskUuid,
  parseApiError,
} from '@novo/shared';

export default function TransactionPage({ params }: { params: { id: string } }) {
  const queryClient = useQueryClient();
  const transfer = useQuery({
    queryKey: ['transfer', params.id],
    queryFn: () => getTransfer(params.id),
  });

  // Supabase Realtime subscription on ledger_entries INSERT for this transaction
  useEffect(() => {
    const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
    const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

    if (!supabaseUrl || !supabaseKey) return;

    const supabase = createClient(supabaseUrl, supabaseKey);
    const channel = supabase
      .channel(`ledger_entries_${params.id}`)
      .on(
        'postgres_changes',
        {
          event: 'INSERT',
          schema: 'public',
          table: 'ledger_entries',
          filter: `transaction_id=eq.${params.id}`,
        },
        () => {
          void queryClient.invalidateQueries({ queryKey: ['transfer', params.id] });
        }
      )
      .subscribe();

    return () => {
      void supabase.removeChannel(channel);
    };
  }, [params.id, queryClient]);

  return (
    <>
      <PageHeader
        title="Transaction Details"
        actions={
          <Link href="/dashboard" className="text-sm font-medium text-sky-700 hover:underline">
            &larr; Back to dashboard
          </Link>
        }
      />
      {transfer.isLoading ? <Spinner label="Loading transaction details..." /> : null}
      {transfer.isError ? (
        <ErrorBox>
          {parseApiError(transfer.error).status === 403
            ? 'You do not have access to this transaction.'
            : parseApiError(transfer.error).message}
        </ErrorBox>
      ) : null}
      {transfer.data ? (
        <Card className="max-w-xl">
          <dl className="grid grid-cols-3 gap-y-3 text-sm">
            <dt className="text-slate-500">Transaction ID</dt>
            <dd className="col-span-2 font-mono text-slate-800">{transfer.data.transactionId}</dd>
            <dt className="text-slate-500">Status</dt>
            <dd className="col-span-2">
              <Badge tone={transfer.data.status === 'completed' || transfer.data.status === 'COMPLETED' ? 'green' : 'yellow'}>
                {transfer.data.status}
              </Badge>
            </dd>
            <dt className="text-slate-500">Amount</dt>
            <dd className="col-span-2 font-semibold text-slate-900">{formatCents(transfer.data.amountCents)}</dd>
            {transfer.data.fromAccount ? (
              <>
                <dt className="text-slate-500">From Account</dt>
                <dd className="col-span-2 font-mono text-slate-800">{maskUuid(transfer.data.fromAccount)}</dd>
              </>
            ) : null}
            {transfer.data.toAccount ? (
              <>
                <dt className="text-slate-500">To Account</dt>
                <dd className="col-span-2 font-mono text-slate-800">{maskUuid(transfer.data.toAccount)}</dd>
              </>
            ) : null}
            <dt className="text-slate-500">Created Time</dt>
            <dd className="col-span-2 text-slate-600">{formatDateTime(transfer.data.createdAt)}</dd>
          </dl>
        </Card>
      ) : null}
    </>
  );
}
