import type { RecommendationDetail } from '../api/types'
import { formatCurrency } from '../lib/format'

export function SavingsBreakdownCard({ rec }: { rec: RecommendationDetail }) {
  const selected = rec.candidates.find((c) => c.selected) ?? rec.candidates[0]
  const monthlySavings = rec.estimatedMonthlySavings
  const annualSavings = monthlySavings != null ? monthlySavings * 12 : null

  return (
    <div className="stat-cards">
      <div className="stat-card">
        <span className="stat-card-label">Monthly saving</span>
        <span className="stat-card-value">{formatCurrency(monthlySavings)}</span>
      </div>
      <div className="stat-card">
        <span className="stat-card-label">Annual projection</span>
        <span className="stat-card-value">{formatCurrency(annualSavings)}</span>
      </div>
      <div className="stat-card">
        <span className="stat-card-label">Two-instance feasibility</span>
        <span className="stat-card-value">
          {selected?.twoInstanceFeasible == null
            ? '—'
            : selected.twoInstanceFeasible
              ? `✓ Feasible (${formatCurrency(selected.twoInstanceMonthlySaving)}/mo)`
              : '✗ Not cheaper'}
        </span>
      </div>
    </div>
  )
}
