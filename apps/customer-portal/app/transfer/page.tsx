'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  Button,
  Card,
  ErrorBox,
  Field,
  PageHeader,
  Spinner,
  createTransfer,
  dollarsToCents,
  formatCents,
  inputClass,
  parseApiError,
  useAuth,
  type TransferResult,
} from '@novo/shared';

const schema = z.object({
  toAccount: z.string().uuid('Enter the destination account ID (UUID)'),
  amount: z
    .string()
    .refine((v) => dollarsToCents(v) !== null, 'Enter an amount like 25 or 25.50 (max 2 decimals)'),
});
type FormValues = z.infer<typeof schema>;

export default function TransferPage() {
  const { accountId, loading } = useAuth();
  // Generated once when the form mounts; only "Reset" creates a new one, so a double click or retry is idempotent.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  const [banner, setBanner] = useState<{ tone: 'red' | 'yellow' | 'green' | 'blue'; text: string } | null>(null);
  const [result, setResult] = useState<TransferResult | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { toAccount: '', amount: '' } });

  const mutation = useMutation({
    mutationFn: createTransfer,
    onSuccess: (data) => {
      setResult(data);
      setBanner(
        data.replayed
          ? { tone: 'blue', text: 'Duplicate request — already processed. No second debit was made.' }
          : { tone: 'green', text: 'Transfer completed.' },
      );
    },
    onError: (error) => {
      const { status, message } = parseApiError(error);
      setResult(null);
      if (status === 422 && /kyc/i.test(message)) setBanner({ tone: 'yellow', text: 'Complete KYC first.' });
      else if (status === 422) setBanner({ tone: 'red', text: message });
      else if (status === 409) setBanner({ tone: 'blue', text: 'Duplicate request — already processed or still in progress.' });
      else if (status === 503) setBanner({ tone: 'red', text: 'Service temporarily unavailable, retry in 30 seconds.' });
      else if (status === 403) setBanner({ tone: 'red', text: 'You can only send from your own account.' });
      else setBanner({ tone: 'red', text: message });
    },
  });

  if (loading) return <Spinner />;
  if (!accountId) {
    return <ErrorBox tone="yellow">Link your account on the <Link href="/dashboard" className="underline">dashboard</Link> before sending money.</ErrorBox>;
  }

  const cents = dollarsToCents(watch('amount') ?? '');

  function onSubmit(values: FormValues) {
    setBanner(null);
    mutation.mutate({
      fromAccount: accountId!,
      toAccount: values.toAccount,
      amountCents: dollarsToCents(values.amount)!,
      idempotencyKey,
    });
  }

  function onReset() {
    setIdempotencyKey(crypto.randomUUID());
    setBanner(null);
    setResult(null);
    reset();
    mutation.reset();
  }

  return (
    <>
      <PageHeader title="New transfer" />
      <div className="max-w-xl space-y-4">
        {banner ? <ErrorBox tone={banner.tone}>{banner.text}</ErrorBox> : null}
        {result ? (
          <Card title="Receipt">
            <p className="text-sm">
              Transaction <span className="font-mono">{result.transfer.transactionId}</span> for {formatCents(result.transfer.amountCents)} is{' '}
              <strong>{result.transfer.status}</strong>.
            </p>
            <Link href={`/transactions/${result.transfer.transactionId}`} className="mt-2 inline-block text-sm text-sky-700 hover:underline">
              View transaction
            </Link>
          </Card>
        ) : null}

        <Card>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="From account">
              <input value={accountId} readOnly className={inputClass} />
            </Field>
            <Field label="To account (ID)" error={errors.toAccount?.message}>
              <input {...register('toAccount')} className={inputClass} placeholder="bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb" />
            </Field>
            <Field label="Amount (USD)" error={errors.amount?.message}>
              <input {...register('amount')} className={inputClass} inputMode="decimal" placeholder="100.00" />
            </Field>
            <p className="text-xs text-slate-500">
              {cents ? `You will send ${formatCents(cents)} (${cents} cents).` : 'Amount is sent as whole cents.'} Request key:{' '}
              <span className="font-mono">{idempotencyKey}</span>
            </p>
            <div className="flex gap-2">
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Sending…' : 'Send money'}
              </Button>
              <Button type="button" variant="secondary" onClick={onReset} disabled={mutation.isPending}>
                Reset
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
