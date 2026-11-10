'use client';

import { NavBar, type NavLink } from '@novo/shared';

const links: NavLink[] = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/transfer', label: 'Transfers' },
  { href: '/loans', label: 'Loans' },
  { href: '/cards', label: 'Cards' },
  { href: '/external-payments', label: 'External Payments' },
  { href: '/statements', label: 'Statements' },
  { href: '/support', label: 'Support' },
  { href: '/profile', label: 'Profile' },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <>
      <NavBar brand="Novo Bank" links={links} />
      <main className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </>
  );
}
