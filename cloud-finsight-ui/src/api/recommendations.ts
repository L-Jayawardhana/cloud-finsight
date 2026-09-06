import { httpClient } from './httpClient'
import type {
  ExplanationResponse,
  Page,
  RecommendationDetail,
  RecommendationSummary,
  RecommendationType,
} from './types'

export interface ListRecommendationsParams {
  vmId?: number
  type?: RecommendationType
  page?: number
  size?: number
  sort?: string
}

export async function listRecommendations(
  params: ListRecommendationsParams = {},
): Promise<Page<RecommendationSummary>> {
  const { data } = await httpClient.get<Page<RecommendationSummary>>('/recommendations', {
    params,
  })
  return data
}

export async function getRecommendation(id: number): Promise<RecommendationDetail> {
  const { data } = await httpClient.get<RecommendationDetail>(`/recommendations/${id}`)
  return data
}

export async function explainRecommendation(id: number): Promise<ExplanationResponse> {
  const { data } = await httpClient.post<ExplanationResponse>(`/recommendations/${id}/explain`)
  return data
}

export async function deleteRecommendation(id: number): Promise<void> {
  await httpClient.delete(`/recommendations/${id}`)
}
