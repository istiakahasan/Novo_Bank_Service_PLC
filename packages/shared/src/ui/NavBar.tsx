'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAuth } from '../auth/AuthContext';
import { Button } from './index';

export interface NavLink {
  href: string;
  label: string;
}

export function NavBar({ brand, links }: { brand: string; links: NavLink[] }) {
  const pathname = usePathname();
  const { authenticated, logout } = useAuth();
  if (!authenticated) return null;
  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <span className="font-semibold text-slate-900">{brand}</span>
        <nav className="flex flex-wrap items-center gap-1 text-sm">
          {links.map((link) => {
            const active = pathname === link.href || pathname.startsWith(link.href + '/');
            return (
              <Link
                key={link.href}
                href={link.href}
                className={`rounded-md px-3 py-1.5 ${active ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-100'}`}
              >
                {link.label}
              </Link>
            );
          })}
          <Button variant="secondary" className="ml-2" onClick={() => void logout()}>
            Sign out
          </Button>
        </nav>
      </div>
    </header>
  );
}
