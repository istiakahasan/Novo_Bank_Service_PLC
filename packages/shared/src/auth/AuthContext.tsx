'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import type { Role, SessionInfo } from '../types';

interface AuthState {
  loading: boolean;
  authenticated: boolean;
  role: Role | null;
  customerId: string | null;
  agentId: string | null;
  accountId: string | null;
  /** re-reads GET /api/session (e.g. after the customer links an account) */
  reload: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

/** Hydrated from the signed httpOnly session cookie through GET /api/session: nothing is kept in localStorage. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionInfo | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    try {
      const response = await fetch('/api/session', { credentials: 'same-origin', cache: 'no-store' });
      setSession(response.ok ? ((await response.json()) as SessionInfo) : null);
    } catch {
      setSession(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const logout = useCallback(async () => {
    await fetch('/api/session', {
      method: 'DELETE',
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    }).catch(() => undefined);
    setSession(null);
    window.location.href = '/login';
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      loading,
      authenticated: session !== null,
      role: session?.role ?? null,
      customerId: session?.customerId ?? null,
      agentId: session?.agentId ?? null,
      accountId: session?.accountId ?? null,
      reload,
      logout,
    }),
    [loading, session, reload, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
