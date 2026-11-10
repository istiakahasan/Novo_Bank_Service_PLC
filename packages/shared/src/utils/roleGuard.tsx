'use client';

import React, { ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../types';

export function useCan(role: Role): boolean {
  const { role: currentRole } = useAuth();
  return currentRole === role;
}

export function RequireRole({
  role,
  fallback = null,
  children,
}: {
  role: Role;
  fallback?: ReactNode;
  children: ReactNode;
}) {
  const can = useCan(role);
  if (!can) return <>{fallback}</>;
  return <>{children}</>;
}

