const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** 123456 -> "$1,234.56" */
export function formatCents(cents: number | null | undefined): string {
  if (cents === null || cents === undefined || Number.isNaN(cents)) return '';
  return usd.format(cents / 100);
}

/** "12.5" -> 1250. Returns null for anything that is not a positive amount with at most 2 decimals. */
export function dollarsToCents(input: string): number | null {
  const trimmed = input.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(trimmed)) return null;
  const cents = Math.round(parseFloat(trimmed) * 100);
  return cents > 0 ? cents : null;
}
