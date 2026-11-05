import type { NextRequest } from 'next/server';
import { createSessionHandlers } from '@novo/shared/server';

export const dynamic = 'force-dynamic';
const handlers = createSessionHandlers('CUSTOMER');

/** POST {username, password}: server-side login against the auth server. */
export async function POST(request: NextRequest) {
  return handlers.login(request);
}
