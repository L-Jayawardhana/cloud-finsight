import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { UtilisationPoint } from '../api/types'

function MetricChart({
  title,
  data,
  p50Key,
  p95Key,
  maxKey,
}: {
  title: string
  data: UtilisationPoint[]
  p50Key: keyof UtilisationPoint
  p95Key: keyof UtilisationPoint
  maxKey: keyof UtilisationPoint
}) {
  return (
    <div>
      <h3>{title}</h3>
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" opacity={0.2} />
          <XAxis dataKey="date" tick={{ fontSize: 12 }} />
          <YAxis tick={{ fontSize: 12 }} unit="%" width={44} />
          <Tooltip />
          <Legend />
          <Line type="monotone" dataKey={p50Key} name="p50" stroke="#0f6e4f" dot={false} />
          <Line type="monotone" dataKey={p95Key} name="p95" stroke="#9c6b0b" dot={false} />
          <Line type="monotone" dataKey={maxKey} name="max" stroke="#a8401d" dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

export function UtilisationChart({
  data,
  loading,
}: {
  data: UtilisationPoint[] | undefined
  loading: boolean
}) {
  if (loading) {
    return (
      <div className="utilisation-charts">
        <div className="skeleton skeleton-card-lg" />
        <div className="skeleton skeleton-card-lg" />
      </div>
    )
  }

  if (!data || data.length === 0) {
    return (
      <div className="empty-state">
        <p>No utilisation data collected yet for this VM.</p>
      </div>
    )
  }

  return (
    <div className="utilisation-charts">
      <MetricChart title="CPU utilisation" data={data} p50Key="cpuP50" p95Key="cpuP95" maxKey="cpuMax" />
      <MetricChart title="Memory utilisation" data={data} p50Key="memP50" p95Key="memP95" maxKey="memMax" />
    </div>
  )
}
