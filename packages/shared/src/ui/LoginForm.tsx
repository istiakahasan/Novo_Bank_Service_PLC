'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
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
  const [isTimedOut, setIsTimedOut] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      if (params.get('reason') === 'timeout') {
        setIsTimedOut(true);
      }
    }
  }, []);

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

      {isTimedOut ? (
        <div
          role="alert"
          className="mb-4 rounded-md border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"
        >
          <div className="flex items-center gap-2 font-medium">
            <svg className="h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            Session expired
          </div>
          <p className="mt-1 text-xs text-amber-700">Your session timed out due to inactivity. Please sign in again.</p>
        </div>
      ) : null}

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
