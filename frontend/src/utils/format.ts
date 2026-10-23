export const money = (n: number | null | undefined, currency = 'USD') => n == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(n);
export const number = (n: number | null | undefined) => n == null ? '—' : new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(n);
export const percent = (n: number | null | undefined, fraction = true) => n == null ? '—' : `${n > 0 ? '+' : ''}${(n * (fraction ? 100 : 1)).toFixed(2)}%`;
export const time = (v?: string | null) => v ? new Date(v).toLocaleString() : 'Never';
