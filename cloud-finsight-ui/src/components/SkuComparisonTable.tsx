import type { RecommendationDetail } from '../api/types'
import { formatCurrency } from '../lib/format'

function generationLabel(generationTag: string | null): string {
  if (!generationTag) return '—'
  return generationTag === 'CURRENT' ? 'CURRENT' : 'OLDER-GEN'
}

export function SkuComparisonTable({ rec }: { rec: RecommendationDetail }) {
  const selected = rec.candidates.find((c) => c.selected) ?? rec.candidates[0]

  return (
    <table className="data-table">
      <thead>
        <tr>
          <th />
          <th>Current</th>
          <th>Recommended</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>SKU</td>
          <td>{rec.currentSku}</td>
          <td>{selected?.candidateSku ?? '—'}</td>
        </tr>
        <tr>
          <td>Generation</td>
          <td>{generationLabel(rec.currentGenerationTag)}</td>
          <td>{generationLabel(selected?.generationTag ?? null)}</td>
        </tr>
        <tr>
          <td>vCPUs</td>
          <td>{rec.currentVcpuCount ?? '—'}</td>
          <td>{selected?.vcpuCount ?? '—'}</td>
        </tr>
        <tr>
          <td>RAM (GB)</td>
          <td>{rec.currentMemoryGb ?? '—'}</td>
          <td>{selected?.memoryGb ?? '—'}</td>
        </tr>
        <tr>
          <td>Price/month</td>
          <td>{formatCurrency(rec.currentMonthlyPrice)}</td>
          <td>{selected ? formatCurrency(selected.estimatedMonthlyCost) : '—'}</td>
        </tr>
      </tbody>
    </table>
  )
}
