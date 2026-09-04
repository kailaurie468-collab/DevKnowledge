import { useEffect, useState } from 'react'
import { adminApi, type AdminOverview } from '@/api/admin'
import { handleAdminError } from './shared'
import { useNotify } from '@/stores/notify'

const overviewCards = [
  { key: 'totalUsers', label: '用户数' },
  { key: 'totalTokens', label: '累计 Token' },
  { key: 'totalRequests', label: '请求数' },
  { key: 'successRate', label: '请求成功率', suffix: '%' },
  { key: 'averageLatencyMs', label: '平均耗时', suffix: ' ms' },
  { key: 'p95LatencyMs', label: 'P95 耗时', suffix: ' ms' },
  { key: 'errorCount', label: '错误数' },
  { key: 'feedbackCount', label: '反馈数' },
] as const

/** 后台-概览：统计卡片 */
export function AdminOverviewSection() {
  const { notify } = useNotify()
  const [overview, setOverview] = useState<AdminOverview | null>(null)

  useEffect(() => {
    adminApi
      .overview()
      .then(setOverview)
      .catch(error => notify(handleAdminError(error, '概览加载失败'), 'error'))
  }, [notify])

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      {overviewCards.map(card => (
        <div
          key={card.key}
          className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900"
        >
          <p className="text-xs text-gray-500 dark:text-gray-400">{card.label}</p>
          <p className="mt-2 text-xl font-semibold text-gray-900 dark:text-gray-100">
            {overview ? formatValue(overview[card.key], card.key === 'successRate' ? 2 : 0) : '-'}
            {'suffix' in card ? card.suffix : ''}
          </p>
        </div>
      ))}
    </div>
  )
}

function formatValue(value: number, digits: number) {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: digits,
  }).format(value)
}
