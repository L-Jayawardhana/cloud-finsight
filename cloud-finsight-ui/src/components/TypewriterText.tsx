import { useEffect, useState } from 'react'

interface TypewriterTextProps {
  text: string
  speed?: number
  charsPerTick?: number
  onComplete?: () => void
}

/**
 * Reveals text progressively to simulate streaming. The backend's /explain and
 * /chat endpoints return a full response in one shot (no real SSE), so this is a
 * client-side reveal of already-fetched text, not a true network stream.
 */
export function TypewriterText({ text, speed = 20, charsPerTick = 3, onComplete }: TypewriterTextProps) {
  const [prevText, setPrevText] = useState(text)
  const [visibleLength, setVisibleLength] = useState(0)
  const [completedText, setCompletedText] = useState<string | null>(null)

  if (text !== prevText) {
    setPrevText(text)
    setVisibleLength(0)
  }

  useEffect(() => {
    if (visibleLength >= text.length) {
      if (completedText !== text) {
        setCompletedText(text)
        onComplete?.()
      }
      return
    }
    const timer = setTimeout(
      () => setVisibleLength((len) => Math.min(len + charsPerTick, text.length)),
      speed,
    )
    return () => clearTimeout(timer)
  }, [visibleLength, text, speed, charsPerTick, onComplete, completedText])

  return <>{text.slice(0, visibleLength)}</>
}
