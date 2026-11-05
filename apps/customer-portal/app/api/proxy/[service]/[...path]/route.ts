import type { NextRequest } from 'next/server';
import { createProxyHandler } from '@novo/shared/server';

export const dynamic = 'force-dynamic';
const proxy = createProxyHandler(['customer', 'ledger', 'support']);

type Context = { params: { service: string; path: string[] } };

export async function GET(request: NextRequest, context: Context) {
  return proxy(request, context);
}
export async function POST(request: NextRequest, context: Context) {
  return proxy(request, context);
}
export async function PATCH(request: NextRequest, context: Context) {
  return proxy(request, context);
}
