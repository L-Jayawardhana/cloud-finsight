import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo } from 'react'
import { sendChatMessage } from './chat'
import { getCostSummary, getRecommendationHistory, getUtilisation, getVms } from './dashboard'
import {
  deleteRecommendation,
  explainRecommendation,
  getRecommendation,
  listRecommendations,
  type ListRecommendationsParams,
} from './recommendations'

export function useVms() {
  return useQuery({ queryKey: ['vms'], queryFn: getVms })
}

export function useCostSummary() {
  return useQuery({ queryKey: ['cost-summary'], queryFn: getCostSummary })
}

export function useRecommendations(params: ListRecommendationsParams = {}) {
  return useQuery({
    queryKey: ['recommendations', params],
    queryFn: () => listRecommendations(params),
  })
}

// listRecommendations returns at most one (the latest pending) recommendation per VM,
// so a page this size covers realistic fleet sizes without needing a dedicated count endpoint.
const RECOMMENDATION_COUNT_PAGE_SIZE = 500

export function useVmRecommendationCounts() {
  const { data, isLoading } = useRecommendations({ size: RECOMMENDATION_COUNT_PAGE_SIZE })
  const counts = useMemo(() => {
    const map = new Map<number, number>()
    data?.content.forEach((rec) => {
      map.set(rec.vmId, (map.get(rec.vmId) ?? 0) + 1)
    })
    return map
  }, [data])
  return { counts, isLoading }
}

export function useRecommendation(id: number) {
  return useQuery({
    queryKey: ['recommendation', id],
    queryFn: () => getRecommendation(id),
    enabled: Number.isFinite(id),
  })
}

export function useRecommendationHistory(vmId: number, page = 0, size = 10) {
  return useQuery({
    queryKey: ['recommendation-history', vmId, page, size],
    queryFn: () => getRecommendationHistory(vmId, page, size),
    enabled: Number.isFinite(vmId),
  })
}

export function useUtilisation(vmId: number, days = 14) {
  return useQuery({
    queryKey: ['utilisation', vmId, days],
    queryFn: () => getUtilisation(vmId, days),
    enabled: Number.isFinite(vmId),
  })
}

export function useExplainRecommendation(id: number) {
  return useMutation({ mutationFn: () => explainRecommendation(id) })
}

export function useDeleteRecommendation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => deleteRecommendation(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recommendations'] })
      queryClient.invalidateQueries({ queryKey: ['cost-summary'] })
    },
  })
}

export function useChatMessage() {
  return useMutation({
    mutationFn: ({ vmId, message }: { vmId: number; message: string }) =>
      sendChatMessage(vmId, message),
  })
}
