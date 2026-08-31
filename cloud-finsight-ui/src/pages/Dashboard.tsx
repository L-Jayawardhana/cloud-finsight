import { useCostSummary, useVmRecommendationCounts, useVms } from '../api/queries'
import { CostSummaryCards } from '../components/CostSummaryCards'
import { DashboardLayout } from '../components/DashboardLayout'
import { VmInventoryTable } from '../components/VmInventoryTable'

export function Dashboard() {
  const costSummary = useCostSummary()
  const vms = useVms()
  const recommendationCounts = useVmRecommendationCounts()

  return (
    <DashboardLayout>
      <h1>Overview</h1>

      <CostSummaryCards data={costSummary.data} loading={costSummary.isLoading} />

      <VmInventoryTable
        vms={vms.data}
        loading={vms.isLoading}
        isError={vms.isError}
        recommendationCounts={recommendationCounts.counts}
      />
    </DashboardLayout>
  )
}
