import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'

type Phase = 'idle' | 'loading' | 'done'

/**
 * A transition affordance, not a real progress readout — this app has no
 * data-router loading state to hook into (plain BrowserRouter + per-page
 * React Query fetches), so it's the standard fixed-duration top bar convention.
 */
export function RouteLoadingBar() {
  const location = useLocation()
  const [phase, setPhase] = useState<Phase>('idle')

  useEffect(() => {
    setPhase('loading')
    const growTimer = setTimeout(() => setPhase('done'), 350)
    const resetTimer = setTimeout(() => setPhase('idle'), 650)
    return () => {
      clearTimeout(growTimer)
      clearTimeout(resetTimer)
    }
  }, [location.pathname])

  return <div className={`route-loading-bar route-loading-bar-${phase}`} />
}
