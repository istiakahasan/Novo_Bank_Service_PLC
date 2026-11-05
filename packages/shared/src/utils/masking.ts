// Same rules as the backend MaskingUtil. Use these everywhere PII is displayed.

/** +8801712345678 -> +8801712***678 ; 01712345678 -> 01712***678 */
export function maskMobile(value: string | null | undefined): string {
  if (!value) return '';
  const m = value.replace(/[\s-]/g, '');
  const head = m.startsWith('+880') ? 8 : m.startsWith('01') ? 5 : 3;
  if (m.length <= head + 3) return '***';
  return m.slice(0, head) + '***' + m.slice(-3);
}

/** rahim@example.com -> r***@example.com */
export function maskEmail(value: string | null | undefined): string {
  if (!value) return '';
  const at = value.indexOf('@');
  if (at < 1) return '***';
  return value[0] + '***' + value.slice(at);
}

/** aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa -> aaaaaaaa... */
export function maskUuid(value: string | null | undefined): string {
  if (!value) return '';
  return value.length <= 8 ? value : value.slice(0, 8) + '…';
}
