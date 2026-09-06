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
      <div>
        <div className="recommendation-card-sku">
          <strong>{rec.vmName}</strong>
          <span className={TYPE_BADGE_CLASS[rec.recommendationType]}>{rec.recommendationType}</span>
        </div>
        <div className="recommendation-card-sku">
          <span>{rec.currentSku}</span>
          <span>→</span>
          <span>{rec.candidateSku ?? '—'}</span>
          {rec.generationTag && (
            <span className={`badge ${isOlderGen ? 'badge-generation-older' : 'badge-generation-current'}`}>
              {isOlderGen ? 'OLDER-GEN' : 'CURRENT'}
            </span>
          )}
        </div>
      </div>

      <div>
        {savings == null && <span className="rec-meta">Savings not yet estimated</span>}
        {savings != null && savings > 0 && (
          <span className="saving-badge">
            💰 {formatCurrency(savings)}/mo · {rec.savingPercent}% saving
          </span>
        )}
        {savings != null && savings < 0 && (
          <span className="cost-increase-badge">
            📈 {formatCurrency(Math.abs(savings))}/mo more
          </span>
        )}
        {savings === 0 && <span className="rec-meta">No cost change</span>}
      </div>

      <span className={CONFIDENCE_BADGE_CLASS[rec.confidenceLevel]}>{rec.confidenceLevel}</span>

      <div className="tag-list">
        {rec.pros.slice(0, 2).map((pro, index) => (
          <span key={`pro-${index}`} className="tag tag-pro" title={pro}>
            {pro}
          </span>
        ))}
        {rec.cons.slice(0, 1).map((con, index) => (
          <span key={`con-${index}`} className="tag tag-con" title={con}>
            {con}
          </span>
        ))}
      </div>

      <div className="card-actions">
        <Link to={`/recommendations/${rec.id}`}>
          <button type="button">View Details</button>
        </Link>
        <Link to={`/recommendations/${rec.id}?explain=1`}>
          <button type="button">Explain with AI</button>
        </Link>
      </div>
    </div>
  )
}
