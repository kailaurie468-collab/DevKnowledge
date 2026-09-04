import { useEffect, useState } from 'react'
import { adminApi, type AdminError, type AdminTraceDetail } from '@/api/admin'

/** 错误详情抽屉：完整堆栈 + 关联请求链路 + 环境信息 */
export function ErrorDetailDrawer({ error, onClose }: { error: AdminError; onClose: () => void }) {
  const [detail, setDetail] = useState<AdminError | null>(null)
  const [traceDetail, setTraceDetail] = useState<AdminTraceDetail | null>(null)

  useEffect(() => {
    // 详情以 id 精确拉取（列表项不含 errorDetail，避免列表响应过大）
    adminApi
      .errorDetail(error.id)
      .then(setDetail)
      .catch(() => setDetail(error))
    if (error.requestId) {
      adminApi
        .traceDetail(error.requestId)
        .then(setTraceDetail)
        .catch(() => setTraceDetail(null))
    }
  }, [error])

  const merged = detail ?? error

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={onClose}>
      <div
        className="h-full w-full max-w-2xl overflow-y-auto bg-white p-6 dark:bg-gray-900"
        onClick={e => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">
              {merged.errorType || 'UnknownError'}
            </h2>
            <p className="mt-1 text-xs text-gray-500">
              {merged.source} · {merged.stage || '-'} ·{' '}
              {new Date(merged.createdAt).toLocaleString('zh-CN')}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
            aria-label="关闭"
          >
            ✕
          </button>
        </div>

        <p className="mb-6 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-400">
          {merged.errorSummary}
        </p>

        {/* 关联请求链路 */}
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">关联请求</h3>
          {!merged.requestId ? (
            <p className="text-xs text-gray-400">无关联请求</p>
          ) : !traceDetail || !traceDetail.trace ? (
            <p className="text-xs text-gray-400">无关联请求记录（trace 可能已被保留策略清理）</p>
          ) : (
            <div className="rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800/70">
              <p>
                {traceDetail.trace.method} {traceDetail.trace.path} · {traceDetail.trace.outcome}
                {traceDetail.trace.statusCode ? ` (${traceDetail.trace.statusCode})` : ''} ·{' '}
                {traceDetail.trace.totalMs} ms
              </p>
              {traceDetail.spans.length > 0 && (
                <ul className="mt-2 space-y-1">
                  {traceDetail.spans.map((span, i) => (
                    <li key={i} className="flex items-center gap-2">
                      <span
                        className="inline-block h-1.5 rounded-full bg-primary-500"
                        style={{ width: `${Math.max(2, Math.min(100, span.durationMs / 10))}%` }}
                      />
                      <span>
                        {span.stage} · {span.status} · {span.durationMs} ms
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </section>

        {/* 环境信息 */}
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">环境</h3>
          <dl className="grid grid-cols-2 gap-2 text-xs text-gray-600 dark:text-gray-400">
            <div>
              <dt className="text-gray-400">页面</dt>
              <dd>{merged.page || '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-400">版本</dt>
              <dd>{merged.appVersion || '-'}</dd>
            </div>
            <div className="col-span-2">
              <dt className="text-gray-400">UserAgent</dt>
              <dd className="break-all">{merged.userAgent || '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-400">耗时</dt>
              <dd>{merged.durationMs != null ? `${merged.durationMs} ms` : '-'}</dd>
            </div>
            <div>
              <dt className="text-gray-400">requestId</dt>
              <dd className="break-all">{merged.requestId || '-'}</dd>
            </div>
          </dl>
        </section>

        {/* 完整堆栈 */}
        <section>
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">堆栈详情</h3>
          {merged.errorDetail ? (
            <pre className="max-h-96 overflow-auto rounded-lg bg-gray-950 p-4 text-xs leading-relaxed text-gray-200">
              {merged.errorDetail}
            </pre>
          ) : (
            <p className="text-xs text-gray-400">无堆栈信息</p>
          )}
        </section>
      </div>
    </div>
  )
}
