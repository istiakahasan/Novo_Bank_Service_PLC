'use client';

import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Card,
  ErrorBox,
  PageHeader,
  Spinner,
  formatDateTime,
  getCustomer,
  kycTone,
  maskEmail,
  maskMobile,
  maskUuid,
  parseApiError,
  useAuth,
} from '@novo/shared';

export default function ProfilePage() {
  const { customerId, loading } = useAuth();
  const customer = useQuery({ queryKey: ['customer', customerId], queryFn: () => getCustomer(customerId!), enabled: !!customerId });

  if (loading) return <Spinner />;

  return (
    <>
      <PageHeader title="Profile" />
      {customer.isLoading ? <Spinner /> : null}
      {customer.isError ? <ErrorBox>{parseApiError(customer.error).message}</ErrorBox> : null}
      {customer.data ? (
        <Card className="max-w-xl">
          <dl className="grid grid-cols-3 gap-y-3 text-sm">
            <dt className="text-slate-500">Name</dt>
            <dd className="col-span-2">{customer.data.displayName}</dd>
            <dt className="text-slate-500">Customer number</dt>
            <dd className="col-span-2 font-mono">{customer.data.customerNumber}</dd>
            <dt className="text-slate-500">Customer ID</dt>
            <dd className="col-span-2 font-mono">{maskUuid(customer.data.id)}</dd>
            <dt className="text-slate-500">Mobile</dt>
            <dd className="col-span-2 font-mono">{maskMobile(customer.data.mobileNo)}</dd>
            <dt className="text-slate-500">Email</dt>
            <dd className="col-span-2 font-mono">{maskEmail(customer.data.email) || '—'}</dd>
            <dt className="text-slate-500">KYC status</dt>
            <dd className="col-span-2">
              <Badge tone={kycTone(customer.data.kycStatus)}>{customer.data.kycStatus.toUpperCase()}</Badge>
            </dd>
            <dt className="text-slate-500">Member since</dt>
            <dd className="col-span-2">{formatDateTime(customer.data.createdAt)}</dd>
          </dl>
          <p className="mt-4 text-xs text-slate-500">Contact details are masked for your protection. Profile editing is not available in this prototype.</p>
        </Card>
      ) : null}
    </>
  );
}
