import { Link } from 'react-router-dom'
import type { ConfidenceLevel, RecommendationSummary, RecommendationType } from '../api/types'
import { formatCurrency } from '../lib/format'

const CONFIDENCE_BADGE_CLASS: Record<ConfidenceLevel, string> = {
  HIGH: 'badge badge-confidence-high',
  MEDIUM: 'badge badge-confidence-medium',
  LOW: 'badge badge-confidence-low',
}

const TYPE_BADGE_CLASS: Record<RecommendationType, string> = {
  DOWNSIZE: 'badge badge-type-downsize',
  UPSIZE: 'badge badge-type-upsize',
  CROSS_GENERATION: 'badge badge-type-cross_generation',
}

export function RecommendationCard({ rec }: { rec: RecommendationSummary }) {
  const isOlderGen = rec.generationTag != null && rec.generationTag !== 'CURRENT'
  const savings = rec.estimatedMonthlySavings

  return (
    <div className="recommendation-card">
      <div className="recommendation-card-header">
        <div>
          <span className="recommendation-card-vm">{rec.vmName}</span>
          <div className="recommendation-card-sku-line">
            <span>{rec.currentSku}</span>
            <span className="recommendation-card-arrow">→</span>
            <span>{rec.candidateSku ?? '—'}</span>
          </div>
        </div>
        <span className={TYPE_BADGE_CLASS[rec.recommendationType]}>{rec.recommendationType}</span>
      </div>

      <div className="recommendation-card-savings">
        {savings == null && <span className="rec-meta">Savings not yet estimated</span>}
        {savings != null && savings > 0 && (
          <>
            <span className="recommendation-card-savings-value">{formatCurrency(savings)}</span>
            <span className="recommendation-card-savings-sub">/mo · {rec.savingPercent}% saving</span>
          </>
        )}
        {savings != null && savings < 0 && (
          <>
            <span className="recommendation-card-savings-value recommendation-card-savings-negative">
              {formatCurrency(Math.abs(savings))}
            </span>
            <span className="recommendation-card-savings-sub">/mo more</span>
          </>
        )}
        {savings === 0 && <span className="rec-meta">No cost change</span>}
      </div>

      <div className="recommendation-card-meta-row">
        <span className={CONFIDENCE_BADGE_CLASS[rec.confidenceLevel]}>{rec.confidenceLevel}</span>
        {rec.generationTag && (
          <span className={`badge ${isOlderGen ? 'badge-generation-older' : 'badge-generation-current'}`}>
            {isOlderGen ? 'OLDER-GEN' : 'CURRENT'}
          </span>
        )}
      </div>

      <ul className="pros-list">
        {rec.pros.slice(0, 2).map((pro, index) => (
          <li key={`pro-${index}`} title={pro}>
            ✓ {pro}
          </li>
        ))}
      </ul>
      <ul className="cons-list">
        {rec.cons.slice(0, 1).map((con, index) => (
          <li key={`con-${index}`} title={con}>
            ⚠ {con}
          </li>
        ))}
      </ul>

      <div className="card-actions">
        <Link to={`/recommendations/${rec.id}`}>
          <button type="button">View Details</button>
        </Link>
        <Link to={`/recommendations/${rec.id}?explain=1`}>
          <button type="button" className="button-primary">
            Explain with AI
          </button>
        </Link>
      </div>
    </div>
  )
}
