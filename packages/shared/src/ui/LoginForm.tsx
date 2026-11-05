'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button, ErrorBox, Field, inputClass } from './index';

const schema = z.object({
  username: z.string().min(1, 'Required'),
  password: z.string().min(1, 'Required'),
});
type FormValues = z.infer<typeof schema>;

/** Posts credentials to this app's /api/session/login; the route handler talks to the auth server and sets httpOnly cookies. */
export function LoginForm({ title, subtitle, redirectTo }: { title: string; subtitle: string; redirectTo: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setError(null);
    const response = await fetch('/api/session/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(values),
    });
    if (response.ok) {
      window.location.href = redirectTo; // full load so the new cookies are picked up by middleware and AuthProvider
      router.refresh();
      return;
    }
    const body = (await response.json().catch(() => ({}))) as { error?: string };
    setError(response.status === 403 ? 'Access denied' : body.error ?? 'Sign-in failed');
  }

  return (
    <div className="mx-auto mt-16 w-full max-w-sm">
      <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
      <p className="mb-6 mt-1 text-sm text-slate-500">{subtitle}</p>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 shadow-sm" noValidate>
        {error ? <ErrorBox>{error}</ErrorBox> : null}
        <Field label="Email or username" error={errors.username?.message}>
          <input {...register('username')} className={inputClass} autoComplete="username" autoFocus />
        </Field>
        <Field label="Password" error={errors.password?.message}>
          <input type="password" {...register('password')} className={inputClass} autoComplete="current-password" />
        </Field>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </div>
  );
}
