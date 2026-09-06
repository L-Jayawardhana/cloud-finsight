import type { ConfidenceLevel } from '../api/types'

const CONFIDENCE_BADGE_CLASS: Record<ConfidenceLevel, string> = {
  HIGH: 'badge badge-confidence-high',
  MEDIUM: 'badge badge-confidence-medium',
  LOW: 'badge badge-confidence-low',
}

export function ConfidenceIndicator({
  confidenceLevel,
  dataCoverageDays,
}: {
  confidenceLevel: ConfidenceLevel
  dataCoverageDays: number | null
}) {
  return (
    <div className="confidence-indicator">
      <span className={CONFIDENCE_BADGE_CLASS[confidenceLevel]}>{confidenceLevel}</span>
      <span className="rec-meta">
        {dataCoverageDays == null
          ? 'No historical metrics collected yet'
          : `Based on ${dataCoverageDays} day${dataCoverageDays === 1 ? '' : 's'} of metrics`}
      </span>
    </div>
  )
}
