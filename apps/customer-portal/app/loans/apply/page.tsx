'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Button,
  Card,
  ErrorBox,
  Field,
  PageHeader,
  Spinner,
  createLoanApplication,
  dollarsToCents,
  formatCents,
  getLoanProducts,
  inputClass,
  parseApiError,
} from '@novo/shared';
import { loanApplySchema, type LoanApplyFormValues } from './schema';

export const dynamic = 'force-dynamic';

function LoanApplyForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialProduct = searchParams.get('product') || '';
  const [inlineError, setInlineError] = useState<string | null>(null);

  const productsQuery = useQuery({
    queryKey: ['loan-products'],
    queryFn: getLoanProducts,
  });

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoanApplyFormValues>({
    resolver: zodResolver(loanApplySchema),
    defaultValues: {
      productCode: initialProduct,
      requestedAmount: '',
      requestedTenureMonths: 12,
    },
  });

  const selectedProductCode = watch('productCode');
  const selectedTenure = watch('requestedTenureMonths');
  const selectedProduct = productsQuery.data?.find((p) => p.productCode === selectedProductCode);

  useEffect(() => {
    if (initialProduct && productsQuery.data?.some((p) => p.productCode === initialProduct)) {
      setValue('productCode', initialProduct);
    }
  }, [initialProduct, productsQuery.data, setValue]);

  const applyMutation = useMutation({
    mutationFn: async (values: LoanApplyFormValues) => {
      const cents = dollarsToCents(values.requestedAmount);
      if (!cents) throw new Error('Invalid dollar amount');

      return await createLoanApplication({
        productCode: values.productCode,
        requestedAmountCents: cents,
        requestedTenureMonths: Number(values.requestedTenureMonths),
      });
    },
    onSuccess: (data) => {
      router.push(`/loans/applications/${data.applicationId}`);
    },
    onError: (err) => {
      const parsed = parseApiError(err);
      if (parsed.status === 422) {
        setInlineError(parsed.message || 'KYC verification required or AML risk tier too high.');
      } else {
        setInlineError(parsed.message);
      }
    },
  });

  const onSubmit = (values: LoanApplyFormValues) => {
    setInlineError(null);
    applyMutation.mutate(values);
  };

  const minTenure = selectedProduct?.minTenureMonths ?? 6;
  const maxTenure = selectedProduct?.maxTenureMonths ?? 240;

  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader
        title="Apply for a Loan"
        actions={
          <Link href="/loans" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            &larr; Back to Loans
          </Link>
        }
      />

      {productsQuery.isLoading ? <Spinner label="Loading loan products..." /> : null}

      {productsQuery.isError ? (
        <ErrorBox>{parseApiError(productsQuery.error).message}</ErrorBox>
      ) : null}

      {productsQuery.data ? (
        <Card>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
            {inlineError ? <ErrorBox>{inlineError}</ErrorBox> : null}

            <Field label="Loan Product" error={errors.productCode?.message}>
              <select
                {...register('productCode')}
                className={inputClass}
                onChange={(e) => {
                  setValue('productCode', e.target.value);
                  const prod = productsQuery.data.find((p) => p.productCode === e.target.value);
                  if (prod) {
                    setValue('requestedTenureMonths', prod.minTenureMonths);
                  }
                }}
              >
                <option value="">Select a product...</option>
                {productsQuery.data.map((product) => (
                  <option key={product.productId} value={product.productCode}>
                    {product.productName} ({product.productCode}) — {(product.baseInterestRateBps / 100).toFixed(2)}% APR
                  </option>
                ))}
              </select>
            </Field>

            {selectedProduct ? (
              <div className="rounded-md bg-slate-50 p-4 text-xs text-slate-600 space-y-1">
                <div className="flex justify-between">
                  <span>Allowed Amount:</span>
                  <span className="font-semibold text-slate-900">
                    {formatCents(selectedProduct.minAmountCents)} – {formatCents(selectedProduct.maxAmountCents)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span>Allowed Tenure:</span>
                  <span className="font-semibold text-slate-900">
                    {selectedProduct.minTenureMonths} – {selectedProduct.maxTenureMonths} months
                  </span>
                </div>
              </div>
            ) : null}

            <Field
              label="Requested Amount (USD)"
              error={errors.requestedAmount?.message}
            >
              <div className="relative">
                <span className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3 text-slate-500">
                  $
                </span>
                <input
                  type="text"
                  placeholder="5000.00"
                  {...register('requestedAmount')}
                  className={`${inputClass} pl-7`}
                />
              </div>
            </Field>

            <Field
              label={`Requested Tenure: ${selectedTenure || minTenure} months`}
              error={errors.requestedTenureMonths?.message}
            >
              <div className="space-y-2">
                <input
                  type="range"
                  min={minTenure}
                  max={maxTenure}
                  step={1}
                  {...register('requestedTenureMonths', { valueAsNumber: true })}
                  className="w-full cursor-pointer accent-slate-900"
                />
                <div className="flex justify-between text-xs text-slate-400">
                  <span>{minTenure} months</span>
                  <span>{maxTenure} months</span>
                </div>
              </div>
            </Field>

            <div className="flex justify-end gap-3 pt-4 border-t border-slate-100">
              <Link href="/loans">
                <Button variant="secondary" type="button">
                  Cancel
                </Button>
              </Link>
              <Button type="submit" disabled={isSubmitting || applyMutation.isPending}>
                {isSubmitting || applyMutation.isPending ? 'Submitting...' : 'Submit Application'}
              </Button>
            </div>
          </form>
        </Card>
      ) : null}
    </div>
  );
}

export default function LoanApplyPage() {
  return (
    <Suspense fallback={<Spinner label="Loading loan application form..." />}>
      <LoanApplyForm />
    </Suspense>
  );
}
