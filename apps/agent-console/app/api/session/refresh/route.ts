import type { NextRequest } from 'next/server';
import { createSessionHandlers } from '@novo/shared/server';

export const dynamic = 'force-dynamic';
const handlers = createSessionHandlers('STAFF');

/** POST rotates the access and refresh cookies using the refresh token cookie. */
export async function POST(request: NextRequest) {
  return handlers.refresh(request);
}
