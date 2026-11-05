'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, Card, ErrorBox, Field, inputClass, useAuth } from '@novo/shared';

const schema = z.object({ accountId: z.string().uuid('Enter a valid account ID (UUID)') });
type FormValues = z.infer<typeof schema>;

/**
 * The backend has no "list my accounts" endpoint, so the customer links the account they operate once;
 * it is stored in the signed httpOnly session cookie (not localStorage).
 */
export function LinkAccountCard() {
  const { reload } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const response = await fetch('/api/session/account', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
      body: JSON.stringify(values),
    });
    if (!response.ok) {
      setError('Could not save the account. Try again.');
      return;
    }
    await reload();
  }

  return (
    <Card title="Link your account">
      <p className="mb-4 text-sm text-slate-600">Enter the ID of the account you want to use. You only need to do this once per sign-in.</p>
      <form onSubmit={handleSubmit(onSubmit)} className="max-w-md space-y-4" noValidate>
        {error ? <ErrorBox>{error}</ErrorBox> : null}
        <Field label="Account ID" error={errors.accountId?.message}>
          <input {...register('accountId')} className={inputClass} placeholder="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" />
        </Field>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : 'Link account'}
        </Button>
      </form>
    </Card>
  );
}
