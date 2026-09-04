import { useEffect, useState } from 'react'
import {
  adminApi,
  type AdminError,
  type AdminPageResponse,
  type AdminRequestTrace,
} from '@/api/admin'
import { ErrorDetailDrawer } from './ErrorDetailDrawer'
import { Pager, formatDate, handleAdminError } from './shared'
import { useNotify } from '@/stores/notify'

/** 后台-请求与错误：traces 分页 + 错误列表（点击开详情抽屉） */
export function AdminTracesSection() {
  const { notify } = useNotify()
  const [tracePage, setTracePage] = useState<AdminPageResponse<AdminRequestTrace> | null>(null)
  const [traceLoading, setTraceLoading] = useState(true)
  const [errors, setErrors] = useState<AdminError[]>([])
  const [selectedError, setSelectedError] = useState<AdminError | null>(null)

  const loadTracePage = (page: number) => {
    setTraceLoading(true)
    adminApi
      .traces(page, tracePage?.size || 20)
      .then(setTracePage)
      .catch(error => notify(handleAdminError(error, '请求记录加载失败'), 'error'))
      .finally(() => setTraceLoading(false))
  }

  useEffect(() => {
    loadTracePage(1)
    adminApi
      .errors()
      .then(setErrors)
      .catch(error => notify(handleAdminError(error, '错误记录加载失败'), 'error'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const traces = tracePage?.items || []

  return (
    <div className="space-y-6">
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">最近请求</h2>
        {traces.length === 0 && !traceLoading ? (
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

      {selectedError && (
        <ErrorDetailDrawer error={selectedError} onClose={() => setSelectedError(null)} />
      )}
    </div>
  )
}
