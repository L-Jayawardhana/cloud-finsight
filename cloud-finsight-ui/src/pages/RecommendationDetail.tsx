import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useChatMessage, useExplainRecommendation, useRecommendation } from '../api/queries'
import type { ChatMessage } from '../api/types'
import { Layout } from '../components/Layout'
import { formatCurrency, formatDate, formatScore } from '../lib/format'

export function RecommendationDetail() {
  const { id } = useParams<{ id: string }>()
  const recommendationId = Number(id)
  const recommendation = useRecommendation(recommendationId)
  const explain = useExplainRecommendation(recommendationId)

  if (recommendation.isLoading) {
    return (
      <Layout>
        <p>Loading recommendation…</p>
      </Layout>
    )
  }

  if (recommendation.isError || !recommendation.data) {
    return (
      <Layout>
        <p className="error-text">Recommendation not found.</p>
        <Link to="/">Back to overview</Link>
      </Layout>
    )
  }

  const rec = recommendation.data

  return (
    <Layout>
      <Link to="/">← Back to overview</Link>
      <h1>
        {rec.recommendationType} — {rec.vmName}
      </h1>
      <p className="rec-meta">
        Status: {rec.status} · Confidence: {rec.confidenceLevel} (score{' '}
        {formatScore(rec.confidenceScore)}) · Created {formatDate(rec.createdAt)}
      </p>
      <p>{rec.summary}</p>
      <p className="savings-highlight">
        Estimated savings: {formatCurrency(rec.estimatedMonthlySavings)}/month
      </p>

      <section>
        <h2>Candidates</h2>
        <table className="data-table">
          <thead>
            <tr>
              <th>SKU</th>
              <th>Generation</th>
              <th>Monthly cost</th>
              <th>Reliability</th>
              <th>Performance</th>
              <th>Pros</th>
              <th>Cons</th>
              <th>Selected</th>
            </tr>
          </thead>
          <tbody>
            {rec.candidates.map((candidate) => (
              <tr key={candidate.id} className={candidate.selected ? 'row-selected' : ''}>
                <td>{candidate.candidateSku}</td>
                <td>{candidate.generationTag}</td>
                <td>{formatCurrency(candidate.estimatedMonthlyCost)}</td>
                <td>{formatScore(candidate.reliabilityScore)}</td>
                <td>{formatScore(candidate.performanceScore)}</td>
                <td>{candidate.pros}</td>
                <td>{candidate.cons}</td>
                <td>{candidate.selected ? '✓' : ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h2>Explain this recommendation</h2>
        <button type="button" onClick={() => explain.mutate()} disabled={explain.isPending}>
          {explain.isPending ? 'Asking…' : 'Explain in plain language'}
        </button>
        {explain.isError && <p className="error-text">Couldn't generate an explanation right now.</p>}
        {explain.data && (
          <p className="explanation-text">
            {explain.data.explanation}
            {explain.data.cached && <span className="cached-badge"> (cached)</span>}
          </p>
        )}
      </section>

      <ChatPanel vmId={rec.vmId} />
    </Layout>
  )
}

function ChatPanel({ vmId }: { vmId: number }) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const chat = useChatMessage()

  const handleSend = () => {
    const text = draft.trim()
    if (!text) return
    setMessages((prev) => [...prev, { role: 'user', content: text }])
    setDraft('')
    chat.mutate(
      { vmId, message: text },
      {
        onSuccess: (response) => {
          setMessages((prev) => [...prev, { role: 'assistant', content: response.reply }])
        },
      },
    )
  }

  return (
    <section>
      <h2>Ask a follow-up question</h2>
      <div className="chat-history">
        {messages.map((message, index) => (
          <p key={index} className={`chat-message chat-message-${message.role}`}>
            <strong>{message.role === 'user' ? 'You' : 'Assistant'}:</strong> {message.content}
          </p>
        ))}
        {chat.isPending && <p className="chat-message chat-message-assistant">Thinking…</p>}
        {chat.isError && <p className="error-text">Something went wrong. Try again.</p>}
      </div>
      <form
        className="chat-input"
        onSubmit={(event) => {
          event.preventDefault()
          handleSend()
        }}
      >
        <input
          type="text"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Why is this recommendation lower confidence?"
        />
        <button type="submit" disabled={chat.isPending || !draft.trim()}>
          Send
        </button>
      </form>
    </section>
  )
}
