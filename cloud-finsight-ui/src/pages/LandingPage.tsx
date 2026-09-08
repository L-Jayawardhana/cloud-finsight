import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

const FEATURES = [
  {
    title: 'Continuous, automatic analysis',
    body: 'Azure Monitor and live retail pricing are polled on independent schedules, and every VM is re-scored on every cycle — no manual audits, no spreadsheets.',
  },
  {
    title: 'Explainable, not a black box',
    body: 'Every recommendation carries a confidence level driven by how much usage history backs it, a scored cost/reliability/performance trade-off, and plain-language pros and cons.',
  },
  {
    title: 'Guardrailed by design',
    body: 'A candidate SKU is only ever suggested if it leaves at least 20% headroom on both CPU and memory — the engine can never recommend a resize that leaves a workload under-resourced.',
  },
  {
    title: 'AI that can’t hallucinate numbers',
    body: '/explain and /chat are grounded in the pipeline’s already-computed savings figures and explicitly instructed never to calculate them itself.',
  },
  {
    title: 'Real auth, real observability',
    body: 'Keycloak-issued JWTs gate every request, and Prometheus, Grafana, and Loki watch both backend services out of the box.',
  },
  {
    title: 'One command to stand it all up',
    body: 'Postgres, Keycloak, both backend services, the dashboard, and the full monitoring stack come up together with docker compose up -d.',
  },
]

const STEPS = [
  {
    label: '01',
    title: 'Collect',
    body: 'collector-service polls Azure Monitor for VM utilisation and the Azure Retail Prices API for live SKU pricing.',
  },
  {
    label: '02',
    title: 'Analyse',
    body: 'A five-stage pipeline aggregates usage, scores candidate SKUs, and calculates real dollar savings.',
  },
  {
    label: '03',
    title: 'Decide',
    body: 'api-service serves the recommendation to your dashboard, with an AI explanation and follow-up chat on demand.',
  },
]

export function LandingPage() {
  const { authenticated, login } = useAuth()

  return (
    <div className="landing">
      <header className="landing-nav">
        <span className="app-brand">cloud-finsight</span>
        <nav className="landing-nav-actions">
          <a
            href="https://github.com/L-Jayawardhana/cloud-finsight"
            className="landing-nav-link"
            target="_blank"
            rel="noreferrer"
          >
            View on GitHub
          </a>
          {authenticated ? (
            <Link to="/dashboard" className="button-primary">
              Go to dashboard
            </Link>
          ) : (
            <button type="button" className="button-primary" onClick={() => login()}>
              Sign in
            </button>
          )}
        </nav>
      </header>

      <main>
        <section className="landing-hero">
          <span className="landing-kicker">AI-assisted cloud cost observability</span>
          <h1 className="landing-hero-title">
            Stop paying for cloud capacity nobody is using.
          </h1>
          <p className="landing-hero-sub">
            Cloud FinSight watches your Azure VM fleet&rsquo;s real utilisation and live retail
            pricing, and turns them into concrete, explainable rightsizing recommendations —
            which VMs are over-provisioned, what to switch them to, how much that saves, and
            why.
          </p>
          <div className="landing-hero-actions">
            {authenticated ? (
              <Link to="/dashboard" className="button-primary landing-cta">
                Go to dashboard
              </Link>
            ) : (
              <button type="button" className="button-primary landing-cta" onClick={() => login()}>
                Sign in to your dashboard
              </button>
            )}
            <a href="#how-it-works" className="landing-cta-secondary">
              See how it works ↓
            </a>
          </div>
        </section>

        <section className="landing-section" id="how-it-works">
          <h2 className="landing-section-title">How it works</h2>
          <div className="landing-steps">
            {STEPS.map((step) => (
              <div className="landing-step" key={step.label}>
                <span className="landing-step-label">{step.label}</span>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="landing-section">
          <h2 className="landing-section-title">Built for real operations, not a demo</h2>
          <div className="landing-features">
            {FEATURES.map((feature) => (
              <div className="landing-feature-card" key={feature.title}>
                <h3>{feature.title}</h3>
                <p>{feature.body}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="landing-section landing-closing">
          <h2 className="landing-section-title">See where your spend is going.</h2>
          <p className="landing-hero-sub">
            Sign in to view live VM inventory, scored recommendations, and AI-grounded
            explanations for your fleet.
          </p>
          {authenticated ? (
            <Link to="/dashboard" className="button-primary landing-cta">
              Go to dashboard
            </Link>
          ) : (
            <button type="button" className="button-primary landing-cta" onClick={() => login()}>
              Sign in
            </button>
          )}
        </section>
      </main>

      <footer className="landing-footer">
        <span>Cloud FinSight — AI-Assisted Cloud Cost Observability and Optimization Platform</span>
      </footer>
    </div>
  )
}
