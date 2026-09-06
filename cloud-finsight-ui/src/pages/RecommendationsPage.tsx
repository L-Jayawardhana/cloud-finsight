import { useState } from 'react'
import { useCostSummary, useRecommendations } from '../api/queries'
import type { RecommendationType } from '../api/types'
import { DashboardLayout } from '../components/DashboardLayout'
import { RecommendationCard } from '../components/RecommendationCard'
import { formatCurrency } from '../lib/format'

const PAGE_SIZE = 12

const SORT_OPTIONS = {
  savings: 'estimatedMonthlySavings,desc',
  confidence: 'confidenceScore,desc',
} as const

type SortKey = keyof typeof SORT_OPTIONS

export function RecommendationsPage() {
  const [typeFilter, setTypeFilter] = useState<RecommendationType | ''>('')
  const [sortKey, setSortKey] = useState<SortKey>('savings')
  const [page, setPage] = useState(0)

  const costSummary = useCostSummary()
  const recommendations = useRecommendations({
    type: typeFilter || undefined,
    sort: SORT_OPTIONS[sortKey],
    page,
    size: PAGE_SIZE,
  })

  const handleTypeChange = (value: RecommendationType | '') => {
    setTypeFilter(value)
    setPage(0)
  }

  const handleSortChange = (value: SortKey) => {
    setSortKey(value)
    setPage(0)
  }

  const data = recommendations.data

  return (
    <DashboardLayout>
      <h1>Recommendations</h1>

      <div className="savings-banner">
        <span>💰 Savings available:</span>
        <span className="savings-banner-amount">
          {costSummary.data ? formatCurrency(costSummary.data.totalPotentialSaving) : '—'}
        </span>
        <span>across all open recommendations</span>
      </div>

      <div className="filter-bar">
        <label>
          Type
          <select
            value={typeFilter}
            onChange={(event) => handleTypeChange(event.target.value as RecommendationType | '')}
          >
            <option value="">All</option>
            <option value="DOWNSIZE">Downsize</option>
            <option value="UPSIZE">Upsize</option>
            <option value="CROSS_GENERATION">Cross-Generation</option>
          </select>
        </label>
        <label>
          Sort by
          <select value={sortKey} onChange={(event) => handleSortChange(event.target.value as SortKey)}>
            <option value="savings">Saving amount</option>
            <option value="confidence">Confidence</option>
          </select>
        </label>
      </div>

      {recommendations.isError && <p className="error-text">Failed to load recommendations.</p>}

      {recommendations.isLoading && (
        <div className="recommendation-cards-grid">
          {Array.from({ length: PAGE_SIZE }).map((_, index) => (
            <div key={index} className="skeleton skeleton-card-lg" />
          ))}
        </div>
      )}

      {!recommendations.isLoading && !recommendations.isError && data && data.content.length === 0 && (
        <div className="empty-state">
          <p>No recommendations yet — data is still being collected.</p>
        </div>
      )}

      {!recommendations.isLoading && !recommendations.isError && data && data.content.length > 0 && (
        <>
          <div className="recommendation-cards-grid">
            {data.content.map((rec) => (
              <RecommendationCard key={rec.id} rec={rec} />
            ))}
          </div>

          <div className="pagination-controls">
            <button type="button" disabled={data.first} onClick={() => setPage((p) => p - 1)}>
              Previous
            </button>
            <span>
              Page {data.number + 1} of {data.totalPages}
            </span>
            <button type="button" disabled={data.last} onClick={() => setPage((p) => p + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </DashboardLayout>
  )
}
