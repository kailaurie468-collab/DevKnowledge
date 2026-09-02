import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  adminApi,
  type AdminError,
  type AdminFeedback,
  type AdminOverview,
  type AdminPageResponse,
  type AdminRequestTrace,
} from '@/api/admin'
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

/**
 * 开发者后台基础页面。
 * 页面不出现在普通用户导航中，接口由后端邮箱白名单做最终鉴权。
 */
export function AdminPage() {
  const { notify } = useNotify()
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [tracePage, setTracePage] = useState<AdminPageResponse<AdminRequestTrace> | null>(null)
  const [traceLoading, setTraceLoading] = useState(false)
  const [errors, setErrors] = useState<AdminError[]>([])
  const [feedback, setFeedback] = useState<AdminFeedback[]>([])
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)

  useEffect(() => {
    Promise.all([adminApi.overview(), adminApi.traces(), adminApi.errors(), adminApi.feedback()])
      .then(([summary, tracePageResult, errorItems, feedbackItems]) => {
        setOverview(summary)
        setTracePage(tracePageResult)
        setErrors(errorItems)
        setFeedback(feedbackItems)
      })
      .catch(error => {
        if (error instanceof Error && isForbiddenError(error.message)) {
          setAccessDenied(true)
        } else {
          notify(error instanceof Error ? error.message : '后台数据加载失败', 'error')
        }
      })
      .finally(() => setLoading(false))
  }, [notify])

  if (accessDenied) {
    return <Navigate to="/" replace />
  }

  if (loading) {
    return <div className="p-6 text-sm text-gray-500">加载后台数据中…</div>
  }

  const loadTracePage = (page: number) => {
    setTraceLoading(true)
    adminApi.traces(page, tracePage?.size || 20)
      .then(setTracePage)
      .catch(error => notify(error instanceof Error ? error.message : '请求记录加载失败', 'error'))
      .finally(() => setTraceLoading(false))
  }

  const traces = tracePage?.items || []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">开发者后台</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">请求、错误和用户反馈概览</p>
      </div>

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {overviewCards.map(card => (
          <div key={card.key} className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
            <p className="text-xs text-gray-500 dark:text-gray-400">{card.label}</p>
            <p className="mt-2 text-xl font-semibold text-gray-900 dark:text-gray-100">
              {overview ? formatValue(overview[card.key], card.key === 'successRate' ? 2 : 0) : '-'}
              {'suffix' in card ? card.suffix : ''}
            </p>
          </div>
        ))}
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">最近请求</h2>
        {traces.length === 0 ? (
          <p className="text-sm text-gray-500">暂无请求记录</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs text-gray-500">
                <tr>
                  <th className="pb-2 pr-4">时间</th>
                  <th className="pb-2 pr-4">接口</th>
                  <th className="pb-2 pr-4">状态</th>
                  <th className="pb-2 pr-4">总耗时</th>
                  <th className="pb-2">首文本</th>
                </tr>
              </thead>
              <tbody className="text-gray-800 dark:text-gray-200">
                {traces.map(item => (
                  <tr key={item.requestId} className="border-t border-gray-100 dark:border-gray-800">
                    <td className="py-2 pr-4 whitespace-nowrap">{formatDate(item.createdAt)}</td>
                    <td className="py-2 pr-4">{item.method} {item.path}</td>
                    <td className="py-2 pr-4">{item.outcome} ({item.statusCode || '-'})</td>
                    <td className="py-2 pr-4">{item.totalMs} ms</td>
                    <td className="py-2">{item.firstTextMs == null ? '-' : `${item.firstTextMs} ms`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {tracePage && tracePage.totalPages > 0 && (
          <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 text-sm dark:border-gray-800">
            <span className="text-gray-500 dark:text-gray-400">
              第 {tracePage.page} / {tracePage.totalPages} 页，共 {tracePage.total} 条
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={!tracePage.hasPrevious || traceLoading}
                onClick={() => loadTracePage(tracePage.page - 1)}
                className="rounded-md border border-gray-300 px-3 py-1.5 text-gray-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600 dark:text-gray-300"
              >
                上一页
              </button>
              <button
                type="button"
                disabled={!tracePage.hasNext || traceLoading}
                onClick={() => loadTracePage(tracePage.page + 1)}
                className="rounded-md border border-gray-300 px-3 py-1.5 text-gray-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600 dark:text-gray-300"
              >
                {traceLoading ? '加载中…' : '下一页'}
              </button>
            </div>
          </div>
        )}
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">最近错误</h2>
        {errors.length === 0 ? (
          <p className="text-sm text-gray-500">暂无错误记录</p>
        ) : (
          <div className="space-y-3">
            {errors.map(item => (
              <article key={item.id} className="rounded-lg bg-gray-50 p-3 text-sm dark:bg-gray-800/70">
                <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  <span>{item.source}</span>
                  <span>{item.errorType || 'UnknownError'}</span>
                  <span>{item.stage || '-'}</span>
                  <span>{formatDate(item.createdAt)}</span>
                </div>
                <p className="mt-1 text-gray-800 dark:text-gray-200">{item.errorSummary}</p>
                <p className="mt-1 text-xs text-gray-500">
                  {item.method} {item.path} · requestId: {item.requestId || '-'}
                </p>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">最近反馈</h2>
        {feedback.length === 0 ? (
          <p className="text-sm text-gray-500">暂无反馈记录</p>
        ) : (
          <div className="space-y-3">
            {feedback.map(item => (
              <article key={item.id} className="rounded-lg bg-gray-50 p-3 text-sm dark:bg-gray-800/70">
                <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  <span>{item.feedbackType}</span>
                  <span>{item.status}</span>
                  <span>{formatDate(item.createdAt)}</span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-gray-800 dark:text-gray-200">{item.content}</p>
                {item.contact && <p className="mt-1 text-xs text-gray-500">联系方式：{item.contact}</p>}
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function formatValue(value: number, digits: number) {
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: digits,
  }).format(value)
}

function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function isForbiddenError(message: string) {
  return /\b403\b/i.test(message) || /forbidden/i.test(message)
}
