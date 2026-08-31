export function formatCurrency(value: number | null | undefined): string {
  return value == null
    ? '—'
    : new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'GBP' }).format(value)
}

export function formatPercent(value: number | null | undefined): string {
  return value == null ? '—' : `${value.toFixed(1)}%`
}

export function formatScore(value: number | null | undefined): string {
  return value == null ? '—' : value.toFixed(2)
}

export function formatDate(value: string): string {
  return new Date(value).toLocaleString()
}
