import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

export type ServiceName = 'customer' | 'ledger' | 'support';

type RetryableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

let refreshInFlight: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  try {
    const response = await fetch('/api/session/refresh', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });
    return response.ok;
  } catch {
    return false;
  }
}

function goToLogin(): void {
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    window.location.href = '/login';
  }
}

/**
 * One axios instance per backend service. Calls go to this app's own /api/proxy/<service>/... route handler,
 * which adds "Authorization: Bearer <accessToken>" from the httpOnly cookie on the server. Browser JavaScript
 * never sees the token, so the usual "interceptor reads the cookie" step happens server-side instead.
 *
 * 401 -> POST /api/session/refresh (rotates the cookies) -> retry once -> a second 401 redirects to /login.
 */
export function createApiClient(service: ServiceName): AxiosInstance {
  const client = axios.create({
    baseURL: `/api/proxy/${service}`,
    timeout: 65_000, // Render free instances can need ~60 s to wake up
    headers: { 'Content-Type': 'application/json' },
  });

  client.interceptors.request.use((config) => {
    // Custom header makes state-changing proxy calls impossible from a cross-site form (CSRF defence).
    config.headers.set('X-Requested-With', 'XMLHttpRequest');
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const original = error.config as RetryableConfig | undefined;
      if (error.response?.status === 401 && original) {
        if (!original._retried) {
          original._retried = true;
          refreshInFlight ??= refreshSession().finally(() => {
            refreshInFlight = null;
          });
          if (await refreshInFlight) {
            return client(original);
          }
        }
        goToLogin();
      }
      return Promise.reject(error);
    },
  );

  return client;
}

export const customerClient = createApiClient('customer');
export const ledgerClient = createApiClient('ledger');
export const supportClient = createApiClient('support');
