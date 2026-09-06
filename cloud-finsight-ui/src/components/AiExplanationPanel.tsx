import { useEffect, useRef } from 'react'
import { useExplainRecommendation } from '../api/queries'
import { TypewriterText } from './TypewriterText'

export function AiExplanationPanel({
  recommendationId,
  autoTrigger,
}: {
  recommendationId: number
  autoTrigger: boolean
}) {
  const explain = useExplainRecommendation(recommendationId)
  const autoTriggered = useRef(false)

  useEffect(() => {
    if (autoTrigger && !autoTriggered.current) {
      autoTriggered.current = true
      explain.mutate()
    }
  }, [autoTrigger, explain])

  return (
    <section className="ai-panel-section">
      <h2>AI Explanation</h2>
      <button type="button" onClick={() => explain.mutate()} disabled={explain.isPending}>
        {explain.isPending ? 'Asking…' : 'Generate Explanation'}
      </button>
      {explain.isError && <p className="error-text">Couldn't generate an explanation right now.</p>}
      {explain.isPending && (
        <div className="explanation-skeleton">
          <span className="skeleton skeleton-text" />
          <span className="skeleton skeleton-text" style={{ width: '80%' }} />
          <span className="skeleton skeleton-text" style={{ width: '60%' }} />
        </div>
      )}
      {explain.data && (
        <p className="explanation-text">
          <TypewriterText text={explain.data.explanation} />
          {explain.data.cached && <span className="cached-badge"> (cached)</span>}
        </p>
      )}
    </section>
  )
}
