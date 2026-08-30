import { httpClient } from './httpClient'

export interface ChatResponse {
  reply: string
}

export async function sendChatMessage(vmId: number, message: string): Promise<ChatResponse> {
  const { data } = await httpClient.post<ChatResponse>('/chat', { vmId, message })
  return data
}
