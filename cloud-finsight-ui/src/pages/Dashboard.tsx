import { Link } from 'react-router-dom'
import { useCostSummary, useRecommendations, useVms } from '../api/queries'
import { Layout } from '../components/Layout'
import { formatCurrency, formatDate, formatPercent } from '../lib/format'

export function Dashboard() {
  const costSummary = useCostSummary()
  const vms = useVms()
  const recommendations = useRecommendations({ size: 10 })

  return (
    <Layout>
      <h1>Overview</h1>

      <section className="stat-cards">
        <StatCard
          label="Monthly spend"
          value={costSummary.data ? formatCurrency(costSummary.data.totalMonthlySpend) : '—'}
          loading={costSummary.isLoading}
        />
        <StatCard
          label="Potential savings"
          value={
            costSummary.data ? formatCurrency(costSummary.data.totalPotentialSaving) : '—'
          }
          loading={costSummary.isLoading}
        />
        <StatCard
          label="VMs monitored"
          value={costSummary.data?.vmCount.toString() ?? '—'}
          loading={costSummary.isLoading}
        />
        <StatCard
          label="Open recommendations"
          value={costSummary.data?.recommendationCount.toString() ?? '—'}
          loading={costSummary.isLoading}
        />
      </section>

      <section>
        <h2>Virtual machines</h2>
        {vms.isLoading && <p>Loading VMs…</p>}
        {vms.isError && <p className="error-text">Failed to load VMs.</p>}
        {vms.data && (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>SKU</th>
                <th>Region</th>
                <th>Monthly price</th>
                <th>P95 CPU</th>
                <th>P95 memory</th>
              </tr>
            </thead>
            <tbody>
              {vms.data.map((vm) => (
                <tr key={vm.id}>
                  <td>{vm.name}</td>
                  <td>{vm.sku}</td>
                  <td>{vm.region}</td>
                  <td>{formatCurrency(vm.currentMonthlyPrice)}</td>
                  <td>{formatPercent(vm.p95CpuPercent)}</td>
                  <td>{formatPercent(vm.p95MemPercent)}</td>
                </tr>
              ))}
              {vms.data.length === 0 && (
                <tr>
                  <td colSpan={6}>No VMs found.</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2>Recommendations</h2>
        {recommendations.isLoading && <p>Loading recommendations…</p>}
        {recommendations.isError && <p className="error-text">Failed to load recommendations.</p>}
        {recommendations.data && (
          <table className="data-table">
            <thead>
              <tr>
                <th>VM</th>
                <th>Type</th>
                <th>Confidence</th>
                <th>Est. monthly savings</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {recommendations.data.content.map((rec) => (
                <tr key={rec.id}>
                  <td>
                    <Link to={`/recommendations/${rec.id}`}>{rec.vmName}</Link>
                  </td>
                  <td>{rec.recommendationType}</td>
                  <td>{rec.confidenceLevel}</td>
                  <td>{formatCurrency(rec.estimatedMonthlySavings)}</td>
                  <td>{rec.status}</td>
                  <td>{formatDate(rec.createdAt)}</td>
                </tr>
              ))}
              {recommendations.data.content.length === 0 && (
                <tr>
                  <td colSpan={6}>No pending recommendations.</td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </section>
    </Layout>
  )
}

function StatCard({
  label,
  value,
  loading,
}: {
  label: string
  value: string
  loading: boolean
}) {
  return (
    <div className="stat-card">
      <span className="stat-card-label">{label}</span>
      <span className="stat-card-value">{loading ? '…' : value}</span>
    </div>
  )
}
