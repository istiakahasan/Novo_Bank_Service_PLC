import type { NextRequest } from 'next/server';
import { createSessionHandlers } from '@novo/shared/server';

export const dynamic = 'force-dynamic';
const handlers = createSessionHandlers('CUSTOMER');

/** POST {accountId}: remembers the account the customer operates (signed cookie). */
export async function POST(request: NextRequest) {
  return handlers.setAccount(request);
}
