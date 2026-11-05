import { describe, expect, it } from 'vitest';
import { dollarsToCents, formatCents } from '../src/utils/currency';
import { maskEmail, maskMobile, maskUuid } from '../src/utils/masking';
import { decodeJwtPayload, signSession, verifySession, type SessionPayload } from '../src/server/session';

const SECRET = 'x'.repeat(40);
const future = () => Math.floor(Date.now() / 1000) + 600;

const session = (over: Partial<SessionPayload> = {}): SessionPayload => ({
  role: 'CUSTOMER',
  customerId: 'c1',
  agentId: 'u1',
  accountId: null,
  exp: future(),
  ...over,
});

describe('masking', () => {
  it('masks mobile numbers like the backend', () => {
    expect(maskMobile('+8801712345678')).toBe('+8801712***678');
    expect(maskMobile('01712345678')).toBe('01712***678');
    expect(maskMobile('0171')).toBe('***');
    expect(maskMobile(null)).toBe('');
  });
  it('masks email and uuid', () => {
    expect(maskEmail('rahim@example.com')).toBe('r***@example.com');
    expect(maskEmail('nope')).toBe('***');
    expect(maskUuid('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')).toBe('aaaaaaaa…');
  });
});

describe('currency', () => {
  it('formats cents', () => {
    expect(formatCents(123456)).toBe('$1,234.56');
    expect(formatCents(0)).toBe('$0.00');
    expect(formatCents(50)).toBe('$0.50');
  });
  it('parses dollars to cents strictly', () => {
    expect(dollarsToCents('12.5')).toBe(1250);
    expect(dollarsToCents('100')).toBe(10000);
    expect(dollarsToCents('0.07')).toBe(7);
    expect(dollarsToCents('0')).toBeNull();
    expect(dollarsToCents('-5')).toBeNull();
    expect(dollarsToCents('1.234')).toBeNull();
    expect(dollarsToCents('abc')).toBeNull();
  });
});

describe('signed session cookie', () => {
  it('round-trips', async () => {
    const token = await signSession(session(), SECRET);
    expect((await verifySession(token, SECRET))?.customerId).toBe('c1');
  });
  it('rejects a tampered role', async () => {
    const [body, sig] = (await signSession(session(), SECRET)).split('.');
    const forged = btoa(JSON.stringify(session({ role: 'STAFF' }))).replace(/=+$/, '');
    expect(await verifySession(`${forged}.${sig}`, SECRET)).toBeNull();
    expect(body).toBeTruthy();
  });
  it('rejects a wrong secret, expiry and garbage', async () => {
    const token = await signSession(session(), SECRET);
    expect(await verifySession(token, 'y'.repeat(40))).toBeNull();
    expect(await verifySession(await signSession(session({ exp: 1 }), SECRET), SECRET)).toBeNull();
    expect(await verifySession('garbage', SECRET)).toBeNull();
    expect(await verifySession(undefined, SECRET)).toBeNull();
    expect(await verifySession(token, undefined)).toBeNull();
  });
});

describe('jwt payload decoding', () => {
  it('reads roles, cid and sub without verifying', () => {
    const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    const jwt = `${b64({ alg: 'RS256' })}.${b64({ sub: 'u1', roles: ['STAFF'], cid: 'c9' })}.sig`;
    expect(decodeJwtPayload(jwt)).toMatchObject({ sub: 'u1', roles: ['STAFF'], cid: 'c9' });
    expect(() => decodeJwtPayload('not-a-jwt')).toThrow();
  });
});
