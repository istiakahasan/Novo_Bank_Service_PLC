// Edge- and Node-compatible (Web Crypto only). Used by middleware and the route handlers.

export const ACCESS_COOKIE = 'novo_at';
export const REFRESH_COOKIE = 'novo_rt';
export const SESSION_COOKIE = 'novo_session';
export const SESSION_TTL_SECONDS = 7 * 24 * 60 * 60; // same as the refresh token

export interface SessionPayload {
  role: 'CUSTOMER' | 'STAFF';
  customerId: string | null;
  agentId: string;
  accountId: string | null;
  /** epoch seconds */
  exp: number;
}

const encoder = new TextEncoder();
const decoder = new TextDecoder();

function toB64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fromB64Url(value: string) {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (value.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign', 'verify']);
}

/** The cookie is "<base64url(json)>.<base64url(hmac-sha256)>": readable, but any edit invalidates it. */
export async function signSession(payload: SessionPayload, secret: string): Promise<string> {
  const body = toB64Url(encoder.encode(JSON.stringify(payload)));
  const signature = await crypto.subtle.sign('HMAC', await hmacKey(secret), encoder.encode(body));
  return `${body}.${toB64Url(new Uint8Array(signature))}`;
}

export async function verifySession(token: string | undefined, secret: string | undefined): Promise<SessionPayload | null> {
  if (!token || !secret) return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  try {
    const valid = await crypto.subtle.verify(
      'HMAC',
      await hmacKey(secret),
      fromB64Url(parts[1]),
      encoder.encode(parts[0]),
    );
    if (!valid) return null;
    const payload = JSON.parse(decoder.decode(fromB64Url(parts[0]))) as SessionPayload;
    if (!payload.exp || payload.exp < Math.floor(Date.now() / 1000)) return null;
    if (payload.role !== 'CUSTOMER' && payload.role !== 'STAFF') return null;
    return payload;
  } catch {
    return null;
  }
}

/** Reads (does not verify) the claims of the access token; the backend verifies the signature on every call. */
export function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const parts = jwt.split('.');
  if (parts.length !== 3) throw new Error('Malformed token');
  return JSON.parse(decoder.decode(fromB64Url(parts[1]))) as Record<string, unknown>;
}

export function getSessionSecret(): string {
  const secret = process.env.SESSION_SECRET;
  if (!secret || secret.length < 32) {
    throw new Error('SESSION_SECRET must be set to at least 32 characters');
  }
  return secret;
}

export function cookieOptions(maxAgeSeconds = SESSION_TTL_SECONDS) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/',
    maxAge: maxAgeSeconds,
  };
}
