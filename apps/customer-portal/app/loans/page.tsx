'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBox,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  formatCents,
  formatDateTime,
  getLoanProducts,
  listLoanApplications,
  maskUuid,
  parseApiError,
  type LoanProduct,
} from '@novo/shared';

export default function LoansPage() {
  const [activeTab, setActiveTab] = useState<'products' | 'applications'>('products');

  const productsQuery = useQuery({
    queryKey: ['loan-products'],
    queryFn: getLoanProducts,
  });

  const applicationsQuery = useQuery({
    queryKey: ['my-loan-applications'],
    queryFn: () => listLoanApplications(),
  });

  const getStatusTone = (status: string) => {
    switch (status) {
      case 'APPROVED':
      case 'DISBURSED':
        return 'green';
      case 'REJECTED':
        return 'red';
      case 'UNDER_REVIEW':
        return 'yellow';
      case 'SUBMITTED':
        return 'blue';
      default:
        return 'gray';
    }
  };

  return (
    <>
      <PageHeader
        title="Loans"
        actions={
          <Link href="/loans/apply">
            <Button>Apply for Loan</Button>
          </Link>
        }
      />

      <div className="mb-6 border-b border-slate-200">
        <nav className="flex space-x-6" aria-label="Loan Sections">
          <button
            type="button"
            onClick={() => setActiveTab('products')}
            className={`border-b-2 pb-3 text-sm font-medium transition ${
              activeTab === 'products'
                ? 'border-slate-900 text-slate-900 font-semibold'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            Loan Products
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('applications')}
            className={`border-b-2 pb-3 text-sm font-medium transition ${
              activeTab === 'applications'
                ? 'border-slate-900 text-slate-900 font-semibold'
                : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700'
            }`}
          >
            My Applications
            {applicationsQuery.data?.length ? (
              <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                {applicationsQuery.data.length}
              </span>
            ) : null}
          </button>
        </nav>
      </div>

      {activeTab === 'products' ? (
        <div>
          {productsQuery.isLoading ? (
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              <SkeletonCard rows={4} />
              <SkeletonCard rows={4} />
              <SkeletonCard rows={4} />
            </div>
          ) : null}

          {productsQuery.isError ? (
            <ErrorBox>{parseApiError(productsQuery.error).message}</ErrorBox>
          ) : null}

          {productsQuery.data ? (
            productsQuery.data.length === 0 ? (
              <EmptyState
                title="No loan products available"
                description="There are currently no active loan offerings. Please check back later."
              />
            ) : (
              <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
                {productsQuery.data.map((product: LoanProduct) => (
                  <Card key={product.productId} className="flex flex-col justify-between">
                    <div>
                      <div className="flex items-center justify-between gap-2">
                        <h3 className="text-lg font-semibold text-slate-900">{product.productName}</h3>
                        <Badge tone="blue">{product.productCode}</Badge>
                      </div>
                      <div className="mt-4 space-y-2 text-sm text-slate-600">
                        <div className="flex justify-between">
                          <span className="text-slate-500">Interest Rate:</span>
                          <span className="font-medium text-slate-900">
                            {(product.baseInterestRateBps / 100).toFixed(2)}% APR
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-500">Loan Amount:</span>
                          <span className="font-medium text-slate-900">
                            {formatCents(product.minAmountCents)} – {formatCents(product.maxAmountCents)}
                          </span>
                        </div>
                        <div className="flex justify-between">
                          <span className="text-slate-500">Tenure Range:</span>
                          <span className="font-medium text-slate-900">
                            {product.minTenureMonths} – {product.maxTenureMonths} months
                          </span>
                        </div>
                      </div>
                    </div>
                    <div className="mt-6 pt-4 border-t border-slate-100">
                      <Link href={`/loans/apply?product=${encodeURIComponent(product.productCode)}`} className="w-full">
                        <Button className="w-full">Apply Now</Button>
                      </Link>
                    </div>
                  </Card>
                ))}
              </div>
            )
          ) : null}
        </div>
      ) : (
        <div>
          {applicationsQuery.isLoading ? <SkeletonTable rows={4} cols={5} /> : null}

          {applicationsQuery.isError ? (
            <ErrorBox>{parseApiError(applicationsQuery.error).message}</ErrorBox>
          ) : null}

          {applicationsQuery.data ? (
            applicationsQuery.data.length === 0 ? (
              <EmptyState
                title="No loan applications yet"
                description="You haven't submitted any loan applications. Apply for a loan to get started."
                action={
                  <Link href="/loans/apply">
                    <Button>Apply for your first loan</Button>
                  </Link>
                }
              />
            ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
                <table className="w-full text-left text-sm" aria-label="My Loan Applications">
                  <caption className="sr-only">List of your loan applications</caption>
                  <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                    <tr>
                      <th scope="col" className="px-4 py-3">Application ID</th>
                      <th scope="col" className="px-4 py-3">Product</th>
                      <th scope="col" className="px-4 py-3">Requested Amount</th>
                      <th scope="col" className="px-4 py-3">Tenure</th>
                      <th scope="col" className="px-4 py-3">Status</th>
                      <th scope="col" className="px-4 py-3">Date</th>
                      <th scope="col" className="px-4 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {applicationsQuery.data.map((app) => (
                      <tr key={app.applicationId} className="hover:bg-slate-50">
                        <td className="px-4 py-3 font-mono text-slate-700">{maskUuid(app.applicationId)}</td>
                        <td className="px-4 py-3 font-medium text-slate-900">{app.productName || app.productCode}</td>
                        <td className="px-4 py-3 font-medium text-slate-900">{formatCents(app.requestedAmountCents)}</td>
                        <td className="px-4 py-3 text-slate-600">{app.requestedTenureMonths} mo</td>
                        <td className="px-4 py-3">
                          <Badge tone={getStatusTone(app.status)}>{app.status}</Badge>
                        </td>
                        <td className="px-4 py-3 text-slate-500">{formatDateTime(app.createdAt)}</td>
                        <td className="px-4 py-3 text-right">
                          <Link
                            href={`/loans/applications/${app.applicationId}`}
                            className="text-sm font-medium text-sky-600 hover:text-sky-800"
                          >
                            View &rarr;
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : null}
        </div>
      )}
    </>
  );
}
