import { formatCurrency } from '../lib/format'
import type { CostSummary } from '../api/types'

interface CostSummaryCardsProps {
  data: CostSummary | undefined
  loading: boolean
}

export function CostSummaryCards({ data, loading }: CostSummaryCardsProps) {
  return (
    <section className="stat-cards">
      <StatCard
        label="Total monthly spend"
        value={data ? formatCurrency(data.totalMonthlySpend) : '—'}
        loading={loading}
      />
      <StatCard
        label="Total potential saving"
        value={data ? formatCurrency(data.totalPotentialSaving) : '—'}
        loading={loading}
      />
      <StatCard
        label="Active VMs"
        value={data ? data.vmCount.toString() : '—'}
        loading={loading}
      />
      <StatCard
        label="Open recommendations"
        value={data ? data.recommendationCount.toString() : '—'}
        loading={loading}
      />
    </section>
  )
}

function StatCard({
  label,
  value,
  loading,
}: {
  label: string
  value: string
  loading: boolean
}) {
  if (loading) {
    return (
      <div className="stat-card">
        <span className="skeleton skeleton-text" style={{ width: '60%' }} />
        <span className="skeleton skeleton-text" style={{ width: '40%', height: '1.5rem' }} />
      </div>
    )
  }

  return (
    <div className="stat-card">
      <span className="stat-card-label">{label}</span>
      <span className="stat-card-value">{value}</span>
      <span className="trend-indicator trend-flat" title="No historical data yet">
        —
      </span>
    </div>
  )
}
