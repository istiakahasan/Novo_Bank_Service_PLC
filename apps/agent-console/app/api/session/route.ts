import type { NextRequest } from 'next/server';
import { createSessionHandlers } from '@novo/shared/server';

export const dynamic = 'force-dynamic';
const handlers = createSessionHandlers('STAFF');

/** POST {accessToken, refreshToken, role?, customerId?} sets the httpOnly cookies. */
export async function POST(request: NextRequest) {
  return handlers.create(request);
}

/** GET returns {role, customerId, agentId, accountId} or 401. */
export async function GET(request: NextRequest) {
  return handlers.get(request);
}

/** DELETE clears the cookies (and revokes the tokens at the auth server). */
export async function DELETE(request: NextRequest) {
  return handlers.remove(request);
}
