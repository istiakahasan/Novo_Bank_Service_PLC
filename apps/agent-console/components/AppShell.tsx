'use client';

import { NavBar, type NavLink } from '@novo/shared';

const links: NavLink[] = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/tickets', label: 'Tickets' },
  { href: '/kyc-queue', label: 'KYC queue' },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <>
      <NavBar brand="Novo Bank Agent Console" links={links} />
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </>
  );
}
