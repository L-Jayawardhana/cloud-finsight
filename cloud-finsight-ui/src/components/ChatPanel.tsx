import { useRef, useState } from 'react'
import { useChatMessage } from '../api/queries'
import { TypewriterText } from './TypewriterText'

interface DisplayMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  animate: boolean
}

export function ChatPanel({ vmId }: { vmId: number }) {
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [draft, setDraft] = useState('')
  const chat = useChatMessage()
  const nextId = useRef(0)

  const handleSend = () => {
    const text = draft.trim()
    if (!text) return
    setMessages((prev) => [...prev, { id: nextId.current++, role: 'user', content: text, animate: false }])
    setDraft('')
    chat.mutate(
      { vmId, message: text },
      {
        onSuccess: (response) => {
          setMessages((prev) => [
            ...prev,
            { id: nextId.current++, role: 'assistant', content: response.reply, animate: true },
          ])
        },
      },
    )
  }

  const markRevealed = (id: number) => {
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, animate: false } : m)))
  }

  return (
    <section className="ai-panel-section">
      <h2>Ask a follow-up question</h2>
      <div className="chat-history">
        {messages.map((message) => (
          <p key={message.id} className={`chat-message chat-message-${message.role}`}>
            <strong>{message.role === 'user' ? 'You' : 'Assistant'}:</strong>{' '}
            {message.animate ? (
              <TypewriterText text={message.content} onComplete={() => markRevealed(message.id)} />
            ) : (
              message.content
            )}
          </p>
        ))}
        {chat.isPending && (
          <p className="chat-message chat-message-assistant typing-indicator-dots">
            <span>●</span>
            <span>●</span>
            <span>●</span>
          </p>
        )}
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
          disabled={chat.isPending}
        />
        <button type="submit" disabled={chat.isPending || !draft.trim()}>
          Send
        </button>
      </form>
    </section>
  )
}
