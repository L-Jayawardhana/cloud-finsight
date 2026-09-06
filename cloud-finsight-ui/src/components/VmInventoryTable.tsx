import { useMemo, useState } from 'react'
import type { VmSummary } from '../api/types'
import { formatCurrency, formatPercent } from '../lib/format'

interface VmInventoryTableProps {
  vms: VmSummary[] | undefined
  loading: boolean
  isError: boolean
  recommendationCounts: Map<number, number>
}

type SortKey =
  | 'name'
  | 'sku'
  | 'region'
  | 'currentMonthlyPrice'
  | 'p95CpuPercent'
  | 'p95MemPercent'
  | 'status'
  | 'recommendations'
type SortDirection = 'asc' | 'desc'
type VmStatus = 'Active' | 'Collecting metrics' | 'Pending pricing'

interface VmRow extends VmSummary {
  status: VmStatus
  recommendationCount: number
}

function getVmStatus(vm: VmSummary): VmStatus {
  if (vm.currentMonthlyPrice == null) return 'Pending pricing'
  if (vm.p95CpuPercent == null || vm.p95MemPercent == null) return 'Collecting metrics'
  return 'Active'
}

const STATUS_BADGE_CLASS: Record<VmStatus, string> = {
  Active: 'badge badge-active',
  'Collecting metrics': 'badge badge-pending',
  'Pending pricing': 'badge badge-pending',
}

const COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'name', label: 'VM Name' },
  { key: 'sku', label: 'SKU' },
  { key: 'region', label: 'Region' },
  { key: 'currentMonthlyPrice', label: 'Monthly Cost (£)' },
  { key: 'p95CpuPercent', label: 'CPU p95 (%)' },
  { key: 'p95MemPercent', label: 'Memory p95 (%)' },
  { key: 'status', label: 'Status' },
  { key: 'recommendations', label: 'Recommendations' },
]

export function VmInventoryTable({ vms, loading, isError, recommendationCounts }: VmInventoryTableProps) {
  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState<SortKey>('name')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')

  const rows: VmRow[] = useMemo(() => {
    if (!vms) return []
    return vms.map((vm) => ({
      ...vm,
      status: getVmStatus(vm),
      recommendationCount: recommendationCounts.get(vm.id) ?? 0,
    }))
  }, [vms, recommendationCounts])

  const filteredRows = useMemo(() => {
    const query = search.trim().toLowerCase()
    if (!query) return rows
    return rows.filter(
      (row) =>
        row.name.toLowerCase().includes(query) ||
        row.sku.toLowerCase().includes(query) ||
        row.region.toLowerCase().includes(query),
    )
  }, [rows, search])

  const sortedRows = useMemo(() => {
    const sorted = [...filteredRows]
    sorted.sort((a, b) => {
      const result = compare(a, b, sortKey)
      return sortDirection === 'asc' ? result : -result
    })
    return sorted
  }, [filteredRows, sortKey, sortDirection])

  const handleSort = (key: SortKey) => {
    if (key === sortKey) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDirection('asc')
    }
  }

  return (
    <section>
      <div className="table-toolbar">
        <h2>Virtual machines</h2>
        <input
          type="search"
          className="table-search"
          placeholder="Search by name, SKU, or region…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          aria-label="Search VMs"
        />
      </div>

      {isError && <p className="error-text">Failed to load VMs.</p>}

      {loading && <VmTableSkeleton />}

      {!loading && !isError && vms && vms.length === 0 && (
        <div className="empty-state">
          <p>No VMs are registered yet.</p>
        </div>
      )}

      {!loading && !isError && vms && vms.length > 0 && (
        <>
          <div className="table-scroll">
            <table className="data-table">
              <thead>
                <tr>
                  {COLUMNS.map((column) => (
                    <th key={column.key} className="sortable-th" onClick={() => handleSort(column.key)}>
                      {column.label}
                      {sortKey === column.key && (
                        <span className="sort-indicator">{sortDirection === 'asc' ? '▲' : '▼'}</span>
                      )}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {sortedRows.map((row) => (
                  <tr key={row.id}>
                    <td>{row.name}</td>
                    <td>{row.sku}</td>
                    <td>{row.region}</td>
                    <td>{formatCurrency(row.currentMonthlyPrice)}</td>
                    <td>{formatPercent(row.p95CpuPercent)}</td>
                    <td>{formatPercent(row.p95MemPercent)}</td>
                    <td>
                      <span className={STATUS_BADGE_CLASS[row.status]}>{row.status}</span>
                    </td>
                    <td>{row.recommendationCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {sortedRows.length === 0 && <p className="rec-meta">No VMs match "{search}".</p>}
        </>
      )}
    </section>
  )
}

function compare(a: VmRow, b: VmRow, key: SortKey): number {
  switch (key) {
    case 'name':
    case 'sku':
    case 'region':
    case 'status':
      return a[key].localeCompare(b[key])
    case 'currentMonthlyPrice':
    case 'p95CpuPercent':
    case 'p95MemPercent':
      return (a[key] ?? -Infinity) - (b[key] ?? -Infinity)
    case 'recommendations':
      return a.recommendationCount - b.recommendationCount
    default:
      return 0
  }
}

function VmTableSkeleton() {
  return (
    <table className="data-table">
      <tbody>
        {Array.from({ length: 5 }).map((_, index) => (
          <tr key={index} className="skeleton-row">
            {COLUMNS.map((column) => (
              <td key={column.key}>
                <span className="skeleton skeleton-text" />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
