export interface VmSummary {
  id: number
  name: string
  sku: string
  region: string
  currentMonthlyPrice: number | null
  p95CpuPercent: number | null
  p95MemPercent: number | null
}

export interface CostSummary {
  totalMonthlySpend: number
  totalPotentialSaving: number
  vmCount: number
  recommendationCount: number
}

export type RecommendationType = 'DOWNSIZE' | 'UPSIZE' | 'CROSS_GENERATION'
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface RecommendationSummary {
  id: number
  vmId: number
  vmName: string
  currentSku: string
  candidateSku: string | null
  generationTag: string | null
  recommendationType: RecommendationType
  confidenceLevel: ConfidenceLevel
  confidenceScore: number | null
  estimatedMonthlySavings: number | null
  savingPercent: number
  status: string
  createdAt: string
  pros: string[]
  cons: string[]
}

export interface RecommendationCandidate {
  id: number
  candidateSku: string
  generationTag: string
  estimatedMonthlyCost: number
  reliabilityScore: number | null
  performanceScore: number | null
  pros: string
  cons: string
  selected: boolean
}

export interface RecommendationDetail {
  id: number
  vmId: number
  vmName: string
  recommendationType: string
  confidenceLevel: string
  confidenceScore: number | null
  estimatedMonthlySavings: number | null
  status: string
  summary: string
  createdAt: string
  candidates: RecommendationCandidate[]
}

export interface RecommendationHistoryEntry {
  id: number
  vmId: number
  vmName: string
  recommendationType: string
  confidenceLevel: string
  estimatedMonthlySavings: number | null
  status: string
  createdAt: string
  isCurrent: boolean
}

export interface ExplanationResponse {
  explanation: string
  cached: boolean
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}
