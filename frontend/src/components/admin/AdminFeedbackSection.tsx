import { useEffect, useState } from 'react'
import {
  adminApi,
  type AdminFeedback,
  type AdminPageResponse,
  type FeedbackStatus,
} from '@/api/admin'
import { Pager, feedbackStatusClass, feedbackStatusLabel, formatDate, handleAdminError } from './shared'
import { useNotify } from '@/stores/notify'

const feedbackStatusTabs = [undefined, 'NEW', 'IN_PROGRESS', 'RESOLVED'] as const

/** 后台-反馈：状态筛选 + 流转 + 分页 */
export function AdminFeedbackSection() {
  const { notify } = useNotify()
  const [feedbackPage, setFeedbackPage] = useState<AdminPageResponse<AdminFeedback> | null>(null)
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState<FeedbackStatus | undefined>(undefined)
  const [pageNum, setPageNum] = useState(1)

  // tab / 页码变化时重新加载
  useEffect(() => {
    setLoading(true)
    adminApi
      .feedback(pageNum, 20, status)
      .then(setFeedbackPage)
      .catch(error => notify(handleAdminError(error, '反馈加载失败'), 'error'))
      .finally(() => setLoading(false))
  }, [status, pageNum, notify])

  const handleStatus = (id: string, next: FeedbackStatus) => {
    adminApi
      .updateFeedbackStatus(id, next)
      .then(() => {
        notify('状态已更新', 'success')
        return adminApi.feedback(pageNum, 20, status)
      })
      .then(setFeedbackPage)
      .catch(err => notify(err instanceof Error ? err.message : '状态更新失败', 'error'))
  }

  const items = feedbackPage?.items || []

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-medium text-gray-900 dark:text-gray-100">用户反馈</h2>
        <div className="flex gap-1 text-sm">
          {feedbackStatusTabs.map(tab => (
            <button
              key={tab ?? 'all'}
              type="button"
              onClick={() => {
                setStatus(tab)
                setPageNum(1)
              }}
              className={`rounded-md px-3 py-1 transition-colors ${
                status === tab
                  ? 'bg-primary-600 text-white'
                  : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
              }`}
            >
              {tab === undefined ? '全部' : feedbackStatusLabel(tab)}
            </button>
          ))}
        </div>
      </div>
      {items.length === 0 && !loading ? (
        <p className="text-sm text-gray-500">暂无反馈记录</p>
      ) : (
        <div className="space-y-3">
          {items.map(item => (
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
                    onClick={() => handleStatus(item.id, 'IN_PROGRESS')}
                    className="rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-600 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
                  >
                    标记处理中
                  </button>
                )}
                {item.status !== 'RESOLVED' && (
                  <button
                    type="button"
                    onClick={() => handleStatus(item.id, 'RESOLVED')}
                    className="rounded-md border border-green-300 px-2 py-1 text-xs text-green-700 hover:bg-green-50 dark:border-green-800 dark:text-green-400 dark:hover:bg-green-900/20"
                  >
                    标记已解决
                  </button>
                )}
                {item.status !== 'NEW' && (
                  <button
                    type="button"
                    onClick={() => handleStatus(item.id, 'NEW')}
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
        <Pager pageInfo={feedbackPage} loading={loading} onPage={setPageNum} />
      )}
    </section>
  )
}
