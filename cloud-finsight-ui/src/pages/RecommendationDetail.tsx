import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useRecommendation, useUtilisation } from '../api/queries'
import { AiExplanationPanel } from '../components/AiExplanationPanel'
import { ChatPanel } from '../components/ChatPanel'
import { ConfidenceIndicator } from '../components/ConfidenceIndicator'
import { DashboardLayout } from '../components/DashboardLayout'
import { ProsConsList } from '../components/ProsConsList'
import { SavingsBreakdownCard } from '../components/SavingsBreakdownCard'
import { SkuComparisonTable } from '../components/SkuComparisonTable'
import { UtilisationChart } from '../components/UtilisationChart'

export function RecommendationDetail() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const recommendationId = Number(id)
  const recommendation = useRecommendation(recommendationId)
  const utilisation = useUtilisation(recommendation.data?.vmId ?? NaN)
  const autoExplain = searchParams.get('explain') === '1'

  if (recommendation.isLoading) {
    return (
      <DashboardLayout>
        <div className="recommendation-detail-layout">
          <div className="skeleton skeleton-card-lg" />
          <div className="skeleton skeleton-card-lg" />
        </div>
      </DashboardLayout>
    )
  }

  if (recommendation.isError || !recommendation.data) {
    return (
      <DashboardLayout>
        <p className="error-text">Recommendation not found.</p>
        <Link to="/">Back to overview</Link>
      </DashboardLayout>
    )
  }

  const rec = recommendation.data
  const selected = rec.candidates.find((c) => c.selected) ?? rec.candidates[0]

  return (
    <DashboardLayout>
      <Link to="/">← Back to overview</Link>
      <h1>
        {rec.recommendationType} — {rec.vmName}
      </h1>

      <div className="recommendation-detail-layout">
        <div className="recommendation-detail-column">
          <ConfidenceIndicator confidenceLevel={rec.confidenceLevel} dataCoverageDays={rec.dataCoverageDays} />
          <p>{rec.summary}</p>

          <section>
            <h2>SKU comparison</h2>
            <SkuComparisonTable rec={rec} />
          </section>

          <section>
            <h2>Savings breakdown</h2>
            <SavingsBreakdownCard rec={rec} />
          </section>

          {selected && (
            <section>
              <h2>Pros &amp; cons</h2>
              <ProsConsList pros={selected.pros} cons={selected.cons} />
            </section>
          )}

          <section>
            <h2>Utilisation (trailing 14 days)</h2>
            <UtilisationChart data={utilisation.data} loading={utilisation.isLoading} />
          </section>
        </div>

        <div className="recommendation-detail-column">
          <AiExplanationPanel recommendationId={rec.id} autoTrigger={autoExplain} />
          <ChatPanel vmId={rec.vmId} />
        </div>
      </div>
    </DashboardLayout>
  )
}
