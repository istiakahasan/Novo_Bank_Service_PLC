'use client';

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
  kycTone,
  listCustomersByKyc,
  maskEmail,
  maskMobile,
  maskUuid,
  parseApiError,
  updateKyc,
  useAuth,
} from '@novo/shared';

export default function KycQueuePage() {
  const { agentId } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const queue = useQuery({
    queryKey: ['kyc-queue', page],
    queryFn: () => listCustomersByKyc('pending', page, 20),
    placeholderData: keepPreviousData,
    retry: false,
  });

  const decide = useMutation({
    mutationFn: ({ id, kycStatus }: { id: string; kycStatus: 'verified' | 'rejected' }) =>
      updateKyc(id, { kycStatus, verifiedBy: agentId! }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['kyc-queue'] }),
  });

  const missingEndpoint = queue.isError && [404, 403, 405].includes(parseApiError(queue.error).status ?? 0);

  return (
    <>
      <PageHeader title="KYC queue" />
      {decide.isError ? <div className="mb-4"><ErrorBox>{parseApiError(decide.error).message}</ErrorBox></div> : null}
      <Card>
        {queue.isLoading ? <Spinner /> : null}
        {missingEndpoint ? (
          <ErrorBox tone="yellow">
            The Customer Service does not expose <code>GET /api/customers?kycStatus=pending</code> yet. Add it as described in docs/FRONTEND.md and redeploy.
          </ErrorBox>
        ) : null}
        {queue.isError && !missingEndpoint ? <ErrorBox>{parseApiError(queue.error).message}</ErrorBox> : null}
        {queue.data && queue.data.content.length === 0 ? <p className="text-sm text-slate-500">No customers are waiting for verification.</p> : null}
        {queue.data && queue.data.content.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-slate-500">
                  <th className="py-2">Customer</th>
                  <th>Number</th>
                  <th>Mobile</th>
                  <th>Email</th>
                  <th>KYC</th>
                  <th>Created</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {queue.data.content.map((c) => (
                  <tr key={c.id} className="border-t border-slate-100">
                    <td className="py-2">
                      {c.displayName} <span className="font-mono text-xs text-slate-400">{maskUuid(c.id)}</span>
                    </td>
                    <td className="font-mono">{c.customerNumber}</td>
                    <td className="font-mono">{maskMobile(c.mobileNo)}</td>
                    <td className="font-mono">{maskEmail(c.email) || '—'}</td>
                    <td>
                      <Badge tone={kycTone(c.kycStatus)}>{c.kycStatus.toUpperCase()}</Badge>
                    </td>
                    <td>{formatDateTime(c.createdAt)}</td>
                    <td className="space-x-2 text-right">
                      <Button disabled={decide.isPending || !agentId} onClick={() => decide.mutate({ id: c.id, kycStatus: 'verified' })}>
                        Verify
                      </Button>
                      <Button variant="danger" disabled={decide.isPending || !agentId} onClick={() => decide.mutate({ id: c.id, kycStatus: 'rejected' })}>
                        Reject
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
        {queue.data ? <Pagination page={page} totalPages={queue.data.totalPages} onChange={setPage} /> : null}
      </Card>
    </>
  );
}
