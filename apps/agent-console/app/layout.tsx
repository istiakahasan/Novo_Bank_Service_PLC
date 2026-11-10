import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { Providers, SessionTimeoutProvider } from '@novo/shared';
import { AppShell } from '@/components/AppShell';
import './globals.css';

export const metadata: Metadata = {
  title: 'Novo Bank Agent Console',
  description: 'Novo Bank prototype',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <SessionTimeoutProvider>
            <AppShell>{children}</AppShell>
          </SessionTimeoutProvider>
        </Providers>
      </body>
    </html>
  );
}
