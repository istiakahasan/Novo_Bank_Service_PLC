'use client';

import { NavBar, type NavLink } from '@novo/shared';

const links: NavLink[] = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/loans/review', label: 'Loan Reviews' },
  { href: '/loans/disburse', label: 'Disburse' },
  { href: '/collections', label: 'Collections' },
  { href: '/treasury', label: 'Treasury' },
  { href: '/reports', label: 'Reports' },
  { href: '/tickets', label: 'Tickets' },
  { href: '/kyc-queue', label: 'KYC Queue' },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <>
      <NavBar brand="Novo Bank Agent Console" links={links} />
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </>
  );
}
