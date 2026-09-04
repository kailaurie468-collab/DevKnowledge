import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import {
  adminApi,
  type AdminError,
  type AdminFeedback,
  type AdminOverview,
  type AdminPageResponse,
  type AdminRequestTrace,
  type AdminUser,
  type FeedbackStatus,
} from '@/api/admin'
import { ErrorDetailDrawer } from '@/components/admin/ErrorDetailDrawer'
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

const feedbackStatusTabs = [undefined, 'NEW', 'IN_PROGRESS', 'RESOLVED'] as const

/**
 * 开发者后台：概览、用户、请求、错误（可点开详情）与反馈管理。
 * 页面不出现在普通用户导航中，接口由后端邮箱白名单做最终鉴权。
 */
export function AdminPage() {
  const { notify } = useNotify()
  const [overview, setOverview] = useState<AdminOverview | null>(null)
  const [tracePage, setTracePage] = useState<AdminPageResponse<AdminRequestTrace> | null>(null)
  const [traceLoading, setTraceLoading] = useState(false)
  const [errors, setErrors] = useState<AdminError[]>([])
  const [users, setUsers] = useState<AdminPageResponse<AdminUser> | null>(null)
  const [userLoading, setUserLoading] = useState(false)
  const [feedbackPage, setFeedbackPage] = useState<AdminPageResponse<AdminFeedback> | null>(null)
  const [feedbackLoading, setFeedbackLoading] = useState(false)
  const [feedbackStatus, setFeedbackStatus] = useState<FeedbackStatus | undefined>(undefined)
  const [feedbackPageNum, setFeedbackPageNum] = useState(1)
  const [selectedError, setSelectedError] = useState<AdminError | null>(null)
  const [loading, setLoading] = useState(true)
  const [accessDenied, setAccessDenied] = useState(false)

  useEffect(() => {
    Promise.all([
      adminApi.overview(),
      adminApi.traces(),
      adminApi.errors(),
      adminApi.users(),
      adminApi.feedback(1, 20),
    ])
      .then(([summary, tracePageResult, errorItems, userPage, feedbackPageResult]) => {
        setOverview(summary)
        setTracePage(tracePageResult)
        setErrors(errorItems)
        setUsers(userPage)
        setFeedbackPage(feedbackPageResult)
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

  // 反馈 tab / 页码变化时重新加载
  useEffect(() => {
    if (loading) return
    setFeedbackLoading(true)
    adminApi
      .feedback(feedbackPageNum, 20, feedbackStatus)
      .then(setFeedbackPage)
      .catch(error => notify(error instanceof Error ? error.message : '反馈加载失败', 'error'))
      .finally(() => setFeedbackLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [feedbackStatus, feedbackPageNum])

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

  const loadUserPage = (page: number) => {
    setUserLoading(true)
    adminApi.users(page, users?.size || 20)
      .then(setUsers)
      .catch(error => notify(error instanceof Error ? error.message : '用户列表加载失败', 'error'))
      .finally(() => setUserLoading(false))
  }

  const handleFeedbackStatus = (id: string, status: FeedbackStatus) => {
    adminApi
      .updateFeedbackStatus(id, status)
      .then(() => {
        notify('状态已更新', 'success')
        return adminApi.feedback(feedbackPageNum, 20, feedbackStatus)
      })
      .then(setFeedbackPage)
      .catch(err => notify(err instanceof Error ? err.message : '状态更新失败', 'error'))
  }

  const traces = tracePage?.items || []
  const feedbackItems = feedbackPage?.items || []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900 dark:text-gray-100">开发者后台</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          请求、错误、用户和反馈概览
        </p>
      </div>

      <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
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
      </section>

      {/* 用户列表 */}
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">用户</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-gray-500">
              <tr>
                <th className="pb-2 pr-4">邮箱</th>
                <th className="pb-2 pr-4">昵称</th>
                <th className="pb-2 pr-4">注册时间</th>
                <th className="pb-2 pr-4">最近活跃</th>
                <th className="pb-2 pr-4">累计 Token</th>
                <th className="pb-2 pr-4">Demo</th>
                <th className="pb-2">反馈</th>
              </tr>
            </thead>
            <tbody className="text-gray-800 dark:text-gray-200">
              {(users?.items ?? []).map(u => (
                <tr key={u.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-4">{u.email}</td>
                  <td className="py-2 pr-4">{u.displayName || '-'}</td>
                  <td className="whitespace-nowrap py-2 pr-4">{formatDate(u.createdAt)}</td>
                  <td className="whitespace-nowrap py-2 pr-4">
                    {u.lastActiveAt ? formatDate(u.lastActiveAt) : '从未'}
                  </td>
                  <td className="py-2 pr-4">{formatValue(u.totalTokens, 0)}</td>
                  <td className="py-2 pr-4">{u.demoCount}</td>
                  <td className="py-2">{u.feedbackCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {users && users.totalPages > 0 && <Pager pageInfo={users} loading={userLoading} onPage={loadUserPage} />}
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
                    <td className="whitespace-nowrap py-2 pr-4">{formatDate(item.createdAt)}</td>
                    <td className="py-2 pr-4">
                      {item.method} {item.path}
                    </td>
                    <td className="py-2 pr-4">
                      {item.outcome} ({item.statusCode || '-'})
                    </td>
                    <td className="py-2 pr-4">{item.totalMs} ms</td>
                    <td className="py-2">{item.firstTextMs == null ? '-' : `${item.firstTextMs} ms`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {tracePage && tracePage.totalPages > 0 && (
          <Pager pageInfo={tracePage} loading={traceLoading} onPage={loadTracePage} />
        )}
      </section>

      {/* 错误列表：点击打开详情抽屉 */}
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">最近错误</h2>
        {errors.length === 0 ? (
          <p className="text-sm text-gray-500">暂无错误记录</p>
        ) : (
          <div className="space-y-3">
            {errors.map(item => (
              <article
                key={item.id}
                onClick={() => setSelectedError(item)}
                className="cursor-pointer rounded-lg bg-gray-50 p-3 text-sm transition-colors hover:bg-gray-100 dark:bg-gray-800/70 dark:hover:bg-gray-800"
              >
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

      {/* 反馈管理：状态筛选 + 流转 */}
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-medium text-gray-900 dark:text-gray-100">用户反馈</h2>
          <div className="flex gap-1 text-sm">
            {feedbackStatusTabs.map(tab => (
              <button
                key={tab ?? 'all'}
                type="button"
                onClick={() => {
                  setFeedbackStatus(tab)
                  setFeedbackPageNum(1)
                }}
                className={`rounded-md px-3 py-1 transition-colors ${
                  feedbackStatus === tab
                    ? 'bg-primary-600 text-white'
                    : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
                }`}
              >
                {tab === undefined ? '全部' : feedbackStatusLabel(tab)}
              </button>
            ))}
          </div>
        </div>
        {feedbackItems.length === 0 && !feedbackLoading ? (
          <p className="text-sm text-gray-500">暂无反馈记录</p>
        ) : (
          <div className="space-y-3">
            {feedbackItems.map(item => (
              <article key={item.id} className="rounded-lg bg-gray-50 p-3 text-sm dark:bg-gray-800/70">
                <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  <span>{item.feedbackType}</span>
                  <span className={feedbackStatusClass(item.status)}>
                    {feedbackStatusLabel(item.status)}
                  </span>
                  <span>{formatDate(item.createdAt)}</span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-gray-800 dark:text-gray-200">
                  {item.content}
                </p>
                {item.contact && <p className="mt-1 text-xs text-gray-500">联系方式：{item.contact}</p>}
                <div className="mt-2 flex flex-wrap gap-2">
                  {item.status === 'NEW' && (
                    <button
                      type="button"
                      onClick={() => handleFeedbackStatus(item.id, 'IN_PROGRESS')}
                      className="rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
                    >
                      标记处理中
                    </button>
                  )}
                  {item.status !== 'RESOLVED' && (
                    <button
                      type="button"
                      onClick={() => handleFeedbackStatus(item.id, 'RESOLVED')}
                      className="rounded-md border border-green-300 px-2 py-1 text-xs text-green-700 hover:bg-green-50 dark:border-green-800 dark:text-green-400 dark:hover:bg-green-900/20"
                    >
                      标记已解决
                    </button>
                  )}
                  {item.status !== 'NEW' && (
                    <button
                      type="button"
                      onClick={() => handleFeedbackStatus(item.id, 'NEW')}
                      className="rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-500 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-400 dark:hover:bg-gray-800"
                    >
                      重新打开
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
        {feedbackPage && feedbackPage.totalPages > 0 && (
          <Pager
            pageInfo={feedbackPage}
            loading={feedbackLoading}
            onPage={setFeedbackPageNum}
          />
        )}
      </section>

      {selectedError && (
        <ErrorDetailDrawer error={selectedError} onClose={() => setSelectedError(null)} />
      )}
    </div>
  )
}

/** 通用分页条：traces / users / feedback 共用 */
function Pager({
  pageInfo,
  loading,
  onPage,
}: {
  pageInfo: AdminPageResponse<unknown>
  loading: boolean
  onPage: (page: number) => void
}) {
  return (
    <div className="mt-4 flex items-center justify-between border-t border-gray-100 pt-3 text-sm dark:border-gray-800">
      <span className="text-gray-500 dark:text-gray-400">
        第 {pageInfo.page} / {pageInfo.totalPages} 页，共 {pageInfo.total} 条
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={!pageInfo.hasPrevious || loading}
          onClick={() => onPage(pageInfo.page - 1)}
          className="rounded-md border border-gray-300 px-3 py-1.5 text-gray-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600 dark:text-gray-300"
        >
          上一页
        </button>
        <button
          type="button"
          disabled={!pageInfo.hasNext || loading}
          onClick={() => onPage(pageInfo.page + 1)}
          className="rounded-md border border-gray-300 px-3 py-1.5 text-gray-600 disabled:cursor-not-allowed disabled:opacity-40 dark:border-gray-600 dark:text-gray-300"
        >
          下一页
        </button>
      </div>
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

function feedbackStatusLabel(status: string) {
  if (status === 'NEW') return '新'
  if (status === 'IN_PROGRESS') return '处理中'
  if (status === 'RESOLVED') return '已解决'
  return status
}

function feedbackStatusClass(status: string) {
  if (status === 'NEW')
    return 'rounded-full bg-blue-100 px-2 py-0.5 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (status === 'IN_PROGRESS')
    return 'rounded-full bg-amber-100 px-2 py-0.5 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
  return 'rounded-full bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/30 dark:text-green-400'
}
