import { httpClient } from './httpClient'
import type { CostSummary, Page, RecommendationHistoryEntry, UtilisationPoint, VmSummary } from './types'

export async function getVms(): Promise<VmSummary[]> {
  const { data } = await httpClient.get<VmSummary[]>('/vms')
  return data
}

export async function getCostSummary(): Promise<CostSummary> {
  const { data } = await httpClient.get<CostSummary>('/cost/summary')
  return data
}

export async function getRecommendationHistory(
  vmId: number,
  page = 0,
  size = 10,
): Promise<Page<RecommendationHistoryEntry>> {
  const { data } = await httpClient.get<Page<RecommendationHistoryEntry>>(
    `/vms/${vmId}/recommendations/history`,
    { params: { page, size } },
  )
  return data
}

export async function getUtilisation(vmId: number, days = 14): Promise<UtilisationPoint[]> {
  const { data } = await httpClient.get<UtilisationPoint[]>(`/vms/${vmId}/utilisation`, {
    params: { days },
  })
  return data
}
