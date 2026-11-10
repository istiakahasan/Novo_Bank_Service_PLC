'use client';

import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@novo/shared';

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const queryClient = useQueryClient();

  useEffect(() => {
    console.error('Unhandled agent console error:', error);
  }, [error]);

  const handleRetry = () => {
    void queryClient.invalidateQueries();
    reset();
  };

  return (
    <div className="mx-auto mt-16 max-w-md rounded-lg border border-rose-200 bg-white p-6 shadow-sm text-center">
      <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
        <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      </div>
      <h2 className="text-lg font-semibold text-slate-900">Something went wrong</h2>
      <p className="mt-2 text-sm text-slate-500">
        {error.message || 'An unexpected error occurred while loading this page.'}
      </p>
      <div className="mt-6 flex justify-center gap-3">
        <Button onClick={handleRetry}>Retry</Button>
      </div>
    </div>
  );
}
