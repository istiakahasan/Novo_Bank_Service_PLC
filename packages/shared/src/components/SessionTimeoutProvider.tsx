'use client';

import React, { ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/AuthContext';

const TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes
const WARNING_MS = 14 * 60 * 1000; // 14 minutes (1 min remaining)

export function SessionTimeoutProvider({ children }: { children: ReactNode }) {
  const { authenticated } = useAuth();
  const [showWarning, setShowWarning] = useState(false);
  const lastActivityRef = useRef<number>(Date.now());
  const warningTimerRef = useRef<NodeJS.Timeout | null>(null);
  const logoutTimerRef = useRef<NodeJS.Timeout | null>(null);

  const performLogout = useCallback(async () => {
    setShowWarning(false);
    try {
      await fetch('/api/session', {
        method: 'DELETE',
        credentials: 'same-origin',
        headers: { 'X-Requested-With': 'XMLHttpRequest' },
      });
    } catch {
      // ignore network errors on logout
    }
    window.location.href = '/login?reason=timeout';
  }, []);

  const resetTimers = useCallback(() => {
    lastActivityRef.current = Date.now();
    setShowWarning(false);

    if (warningTimerRef.current) clearTimeout(warningTimerRef.current);
    if (logoutTimerRef.current) clearTimeout(logoutTimerRef.current);

    if (!authenticated) return;

    warningTimerRef.current = setTimeout(() => {
      setShowWarning(true);
    }, WARNING_MS);

    logoutTimerRef.current = setTimeout(() => {
      void performLogout();
    }, TIMEOUT_MS);
  }, [authenticated, performLogout]);

  useEffect(() => {
    if (!authenticated) {
      setShowWarning(false);
      return;
    }

    resetTimers();

    const handleUserActivity = () => {
      // Throttle activity resets to at most once per 2 seconds
      if (Date.now() - lastActivityRef.current > 2000) {
        resetTimers();
      }
    };

    const events = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'];
    events.forEach((evt) => window.addEventListener(evt, handleUserActivity, { passive: true }));

    return () => {
      events.forEach((evt) => window.removeEventListener(evt, handleUserActivity));
      if (warningTimerRef.current) clearTimeout(warningTimerRef.current);
      if (logoutTimerRef.current) clearTimeout(logoutTimerRef.current);
    };
  }, [authenticated, resetTimers]);

  return (
    <>
      {children}
      {showWarning ? (
        <div
          role="alert"
          aria-live="assertive"
          className="fixed bottom-4 right-4 z-50 flex max-w-md items-center justify-between gap-4 rounded-lg border border-amber-300 bg-amber-50 p-4 shadow-lg text-amber-900 animate-bounce"
        >
          <div className="flex items-center gap-2">
            <svg className="h-5 w-5 text-amber-600 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span className="text-sm font-medium">Session expires in 1 minute</span>
          </div>
          <button
            type="button"
            onClick={resetTimers}
            className="rounded bg-amber-600 px-3 py-1 text-xs font-semibold text-white shadow-sm hover:bg-amber-500"
          >
            Stay signed in
          </button>
        </div>
      ) : null}
    </>
  );
}
