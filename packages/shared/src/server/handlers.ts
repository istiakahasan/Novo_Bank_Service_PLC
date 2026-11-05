import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  SESSION_COOKIE,
  SESSION_TTL_SECONDS,
  SessionPayload,
  cookieOptions,
  decodeJwtPayload,
  getSessionSecret,
  signSession,
  verifySession,
} from './session';

type AllowedRole = 'CUSTOMER' | 'STAFF';
type ServiceName = 'customer' | 'ledger' | 'support';

interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  role?: string;
  customerId?: string;
}

const loginSchema = z.object({ username: z.string().min(1).max(100), password: z.string().min(1).max(200) });
const tokenSchema = z.object({
  accessToken: z.string().min(10),
  refreshToken: z.string().min(10),
  role: z.string().optional(),
  customerId: z.string().uuid().optional(),
});
const accountSchema = z.object({ accountId: z.string().uuid() });

function authUrl(path: string): string {
  const base = (process.env.NEXT_PUBLIC_AUTH_URL ?? '').replace(/\/+$/, '');
  if (!base) throw new Error('NEXT_PUBLIC_AUTH_URL is not set');
  return base + path;
}

const LOGIN_PATH = () => process.env.AUTH_LOGIN_PATH ?? '/auth/login';
const REFRESH_PATH = () => process.env.AUTH_REFRESH_PATH ?? '/auth/refresh';
const LOGOUT_PATH = () => process.env.AUTH_LOGOUT_PATH ?? '/auth/logout';

function json(body: unknown, status = 200): NextResponse {
  return NextResponse.json(body, { status, headers: { 'Cache-Control': 'no-store' } });
}

function clearCookies(response: NextResponse): NextResponse {
  for (const name of [ACCESS_COOKIE, REFRESH_COOKIE, SESSION_COOKIE]) {
    response.cookies.set(name, '', { ...cookieOptions(0), maxAge: 0 });
  }
  return response;
}

async function readSession(request: NextRequest): Promise<SessionPayload | null> {
  return verifySession(request.cookies.get(SESSION_COOKIE)?.value, process.env.SESSION_SECRET);
}

/** Session endpoints for one app. allowedRole is the only role that may establish a session in that app. */
export function createSessionHandlers(allowedRole: AllowedRole) {
  async function establish(tokens: AuthTokens, accountId: string | null = null): Promise<NextResponse> {
    let claims: Record<string, unknown>;
    try {
      claims = decodeJwtPayload(tokens.accessToken);
    } catch {
      return json({ error: 'Invalid token received from the auth server' }, 502);
    }
    const roles = Array.isArray(claims.roles) ? (claims.roles as string[]) : [];
    const role = roles[0] ?? tokens.role;
    if (role !== allowedRole) {
      // Wrong portal for this account: no cookies are set at all.
      return json({ error: 'Access denied' }, 403);
    }
    const customerId = typeof claims.cid === 'string' ? claims.cid : tokens.customerId ?? null;
    const agentId = String(claims.sub ?? '');
    const session: SessionPayload = {
      role: allowedRole,
      customerId,
      agentId,
      accountId,
      exp: Math.floor(Date.now() / 1000) + SESSION_TTL_SECONDS,
    };
    const response = json({ role: allowedRole, customerId, agentId, accountId });
    response.cookies.set(ACCESS_COOKIE, tokens.accessToken, cookieOptions());
    response.cookies.set(REFRESH_COOKIE, tokens.refreshToken, cookieOptions());
    response.cookies.set(SESSION_COOKIE, await signSession(session, getSessionSecret()), cookieOptions());
    return response;
  }

  return {
    /** POST /api/session/login {username,password}: logs in on the server, so tokens never reach browser JavaScript. */
    async login(request: NextRequest): Promise<NextResponse> {
      const parsed = loginSchema.safeParse(await request.json().catch(() => null));
      if (!parsed.success) return json({ error: 'Username and password are required' }, 400);
      let upstream: Response;
      try {
        upstream = await fetch(authUrl(LOGIN_PATH()), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
          body: JSON.stringify(parsed.data),
          signal: AbortSignal.timeout(65_000),
          cache: 'no-store',
        });
      } catch {
        return json({ error: 'The sign-in service is unavailable. Retry in 30 seconds.' }, 503);
      }
      if (upstream.status === 401) return json({ error: 'Invalid username or password' }, 401);
      if (upstream.status === 429) return json({ error: 'Too many failed attempts. Try again in 15 minutes.' }, 429);
      if (!upstream.ok) return json({ error: 'Sign-in failed' }, 502);
      return establish((await upstream.json()) as AuthTokens);
    },

    /** POST /api/session {accessToken,refreshToken,role?,customerId?}: the token-handover variant of login. */
    async create(request: NextRequest): Promise<NextResponse> {
      const parsed = tokenSchema.safeParse(await request.json().catch(() => null));
      if (!parsed.success) return json({ error: 'Invalid session payload' }, 400);
      return establish(parsed.data);
    },

    /** GET /api/session */
    async get(request: NextRequest): Promise<NextResponse> {
      const session = await readSession(request);
      if (!session) return json({ error: 'Unauthenticated' }, 401);
      return json({ role: session.role, customerId: session.customerId, agentId: session.agentId, accountId: session.accountId });
    },

    /** DELETE /api/session: revokes the tokens at the auth server (best effort) and clears the cookies. */
    async remove(request: NextRequest): Promise<NextResponse> {
      const access = request.cookies.get(ACCESS_COOKIE)?.value;
      const refresh = request.cookies.get(REFRESH_COOKIE)?.value;
      if (access && refresh) {
        await fetch(authUrl(LOGOUT_PATH()), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${access}` },
          body: JSON.stringify({ refreshToken: refresh }),
          signal: AbortSignal.timeout(10_000),
          cache: 'no-store',
        }).catch(() => undefined);
      }
      return clearCookies(json({ ok: true }));
    },

    /** POST /api/session/refresh: rotates the access and refresh cookies. */
    async refresh(request: NextRequest): Promise<NextResponse> {
      const session = await readSession(request);
      const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
      if (!session || !refreshToken) return clearCookies(json({ error: 'Unauthenticated' }, 401));
      let upstream: Response;
      try {
        upstream = await fetch(authUrl(REFRESH_PATH()), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken }),
          signal: AbortSignal.timeout(65_000),
          cache: 'no-store',
        });
      } catch {
        return json({ error: 'The sign-in service is unavailable' }, 503);
      }
      if (!upstream.ok) return clearCookies(json({ error: 'Session expired' }, 401));
      const tokens = (await upstream.json()) as AuthTokens;
      const response = json({ ok: true });
      response.cookies.set(ACCESS_COOKIE, tokens.accessToken, cookieOptions());
      response.cookies.set(REFRESH_COOKIE, tokens.refreshToken, cookieOptions());
      return response;
    },

    /** POST /api/session/account {accountId}: remembers which account the customer operates (signed cookie). */
    async setAccount(request: NextRequest): Promise<NextResponse> {
      const session = await readSession(request);
      if (!session) return json({ error: 'Unauthenticated' }, 401);
      if (request.headers.get('x-requested-with') !== 'XMLHttpRequest') return json({ error: 'Forbidden' }, 403);
      const parsed = accountSchema.safeParse(await request.json().catch(() => null));
      if (!parsed.success) return json({ error: 'accountId must be a UUID' }, 400);
      const updated: SessionPayload = { ...session, accountId: parsed.data.accountId };
      const response = json({ accountId: parsed.data.accountId });
      response.cookies.set(SESSION_COOKIE, await signSession(updated, getSessionSecret()), cookieOptions());
      return response;
    },
  };
}

const SERVICE_URLS: Record<ServiceName, () => string | undefined> = {
  customer: () => process.env.NEXT_PUBLIC_CUSTOMER_SERVICE_URL,
  ledger: () => process.env.NEXT_PUBLIC_LEDGER_SERVICE_URL,
  support: () => process.env.NEXT_PUBLIC_SUPPORT_SERVICE_URL,
};

/**
 * Authenticated pass-through to a fixed set of backend services (not an open relay): requires a valid signed
 * session, adds the Bearer token from the httpOnly cookie, and requires X-Requested-With on non-GET calls.
 */
export function createProxyHandler(allowed: ServiceName[]) {
  return async function proxy(
    request: NextRequest,
    context: { params: { service: string; path: string[] } },
  ): Promise<NextResponse> {
    const service = context.params.service as ServiceName;
    if (!allowed.includes(service)) return json({ error: 'Unknown service' }, 404);
    if (!(await readSession(request))) return json({ status: 401, message: 'Unauthenticated' }, 401);
    if (request.method !== 'GET' && request.headers.get('x-requested-with') !== 'XMLHttpRequest') {
      return json({ status: 403, message: 'Forbidden' }, 403);
    }
    const token = request.cookies.get(ACCESS_COOKIE)?.value;
    if (!token) return json({ status: 401, message: 'Unauthenticated' }, 401);

    const base = (SERVICE_URLS[service]() ?? '').replace(/\/+$/, '');
    if (!base) return json({ status: 500, message: `Service URL for "${service}" is not configured` }, 500);
    if (context.params.path.some((segment) => segment === '..' || segment === '.' || segment === '')) {
      return json({ status: 400, message: 'Bad path' }, 400);
    }
    const target = `${base}/${context.params.path.map(encodeURIComponent).join('/')}${request.nextUrl.search}`;

    let upstream: Response;
    try {
      upstream = await fetch(target, {
        method: request.method,
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'application/json',
          ...(request.method === 'GET' || request.method === 'HEAD' ? {} : { 'Content-Type': 'application/json' }),
        },
        body: request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.text(),
        signal: AbortSignal.timeout(65_000),
        cache: 'no-store',
      });
    } catch {
      return json({ status: 503, message: 'Service temporarily unavailable, retry in 30 seconds' }, 503);
    }
    const text = await upstream.text();
    return new NextResponse(text.length ? text : null, {
      status: upstream.status,
      headers: { 'Content-Type': upstream.headers.get('content-type') ?? 'application/json', 'Cache-Control': 'no-store' },
    });
  };
}
