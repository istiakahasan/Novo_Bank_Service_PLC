import { NextResponse, type NextRequest } from 'next/server';
import { SESSION_COOKIE, verifySession } from '@novo/shared/session';

// Sessions are HMAC-signed cookies: a missing, expired or edited cookie, or a role other than CUSTOMER, goes to /login.
export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const session = await verifySession(request.cookies.get(SESSION_COOKIE)?.value, process.env.SESSION_SECRET);
  const allowed = session?.role === 'CUSTOMER';

  if (pathname === '/login') {
    return allowed ? NextResponse.redirect(new URL('/dashboard', request.url)) : NextResponse.next();
  }
  if (!allowed) {
    const url = new URL('/login', request.url);
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  // the /api routes do their own checks (login must stay reachable)
  matcher: ['/((?!api/|_next/|favicon.ico).*)'],
};
