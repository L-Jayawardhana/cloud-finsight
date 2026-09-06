import { useState } from 'react'
import { useVms } from '../api/queries'
import { ChatPanel } from '../components/ChatPanel'
import { DashboardLayout } from '../components/DashboardLayout'

export function ChatPage() {
  const vms = useVms()
  const [selectedVmId, setSelectedVmId] = useState<number | null>(null)

  return (
    <DashboardLayout>
      <h1>Chat</h1>

      <div className="filter-bar">
        <label>
          VM
          <select
            value={selectedVmId ?? ''}
            onChange={(event) => setSelectedVmId(event.target.value ? Number(event.target.value) : null)}
          >
            <option value="">Select a VM…</option>
            {vms.data?.map((vm) => (
              <option key={vm.id} value={vm.id}>
                {vm.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {vms.isLoading && <p>Loading VMs…</p>}
      {vms.isError && <p className="error-text">Failed to load VMs.</p>}

      {selectedVmId == null ? (
        <div className="empty-state">
          <p>Select a VM above to start asking questions about its recommendations.</p>
        </div>
      ) : (
        <ChatPanel key={selectedVmId} vmId={selectedVmId} />
      )}
    </DashboardLayout>
  )
}
