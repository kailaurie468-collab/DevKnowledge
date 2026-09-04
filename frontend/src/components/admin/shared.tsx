import type { AdminPageResponse } from '@/api/admin'

/** 后台区块共用：分页条 / 日期格式化 / 反馈状态展示 / 403 处理 */

/** 403 时通知 AdminPage 壳统一弹回首页（非管理员访问子路由） */
export function handleAdminError(error: unknown, fallback: string): string {
  const message = error instanceof Error ? error.message : fallback
  if (/\b403\b/i.test(message) || /forbidden/i.test(message)) {
    window.dispatchEvent(new CustomEvent('admin-access-denied'))
  }
  return message
}

export function Pager({
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

export function formatDate(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

export function feedbackStatusLabel(status: string) {
  if (status === 'NEW') return '新'
  if (status === 'IN_PROGRESS') return '处理中'
  if (status === 'RESOLVED') return '已解决'
  return status
}

export function feedbackStatusClass(status: string) {
  if (status === 'NEW')
    return 'rounded-full bg-blue-100 px-2 py-0.5 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (status === 'IN_PROGRESS')
    return 'rounded-full bg-amber-100 px-2 py-0.5 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
  return 'rounded-full bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/30 dark:text-green-400'
}
