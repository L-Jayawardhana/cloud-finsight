import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { sendChatMessage } from './chat'
import { getCostSummary, getRecommendationHistory, getVms } from './dashboard'
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
