import type { ButtonHTMLAttributes, ReactNode } from 'react';
import type { KycStatus, TicketPriority, TicketStatus } from '../types';

type Tone = 'gray' | 'green' | 'yellow' | 'red' | 'blue' | 'purple';

const toneClasses: Record<Tone, string> = {
  gray: 'bg-slate-100 text-slate-700',
  green: 'bg-emerald-100 text-emerald-800',
  yellow: 'bg-amber-100 text-amber-800',
  red: 'bg-rose-100 text-rose-800',
  blue: 'bg-sky-100 text-sky-800',
  purple: 'bg-violet-100 text-violet-800',
};

export function Badge({ tone = 'gray', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${toneClasses[tone]}`}>
      {children}
    </span>
  );
}

export function kycTone(status: KycStatus | string): Tone {
  return status === 'verified' ? 'green' : status === 'rejected' ? 'red' : 'yellow';
}

export function priorityTone(priority: TicketPriority | string): Tone {
  return priority === 'URGENT' ? 'red' : priority === 'HIGH' ? 'yellow' : priority === 'MEDIUM' ? 'blue' : 'gray';
}

export function statusTone(status: TicketStatus | string): Tone {
  switch (status) {
    case 'OPEN':
      return 'blue';
    case 'IN_PROGRESS':
      return 'purple';
    case 'PENDING_CUSTOMER':
      return 'yellow';
    case 'RESOLVED':
      return 'green';
    default:
      return 'gray';
  }
}

export function Card({ title, children, className = '' }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-lg border border-slate-200 bg-white p-5 shadow-sm ${className}`}>
      {title ? <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">{title}</h2> : null}
      {children}
    </section>
  );
}

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
      <div className="flex items-center gap-2">{actions}</div>
    </div>
  );
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-slate-500" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-slate-600" />
      {label}
    </div>
  );
}

export function ErrorBox({ children, tone = 'red' }: { children: ReactNode; tone?: 'red' | 'yellow' | 'green' | 'blue' }) {
  const classes = {
    red: 'border-rose-200 bg-rose-50 text-rose-800',
    yellow: 'border-amber-200 bg-amber-50 text-amber-800',
    green: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    blue: 'border-sky-200 bg-sky-50 text-sky-800',
  }[tone];
  return (
    <div className={`rounded-md border px-4 py-3 text-sm ${classes}`} role="alert">
      {children}
    </div>
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'danger' };

export function Button({ variant = 'primary', className = '', ...props }: ButtonProps) {
  const variants = {
    primary: 'bg-slate-900 text-white hover:bg-slate-700',
    secondary: 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50',
    danger: 'bg-rose-600 text-white hover:bg-rose-500',
  };
  return (
    <button
      {...props}
      className={`inline-flex items-center justify-center rounded-md px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
    />
  );
}

export const inputClass =
  'block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none focus:ring-1 focus:ring-slate-500 disabled:bg-slate-100';

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-sm font-medium text-slate-700">{label}</span>
      {children}
      {error ? <span className="block text-xs text-rose-600">{error}</span> : null}
    </label>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="mt-4 flex items-center justify-between text-sm text-slate-600">
      <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Next
      </Button>
    </div>
  );
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '';
  return new Date(value).toLocaleString();
}
