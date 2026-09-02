import { useEffect, useRef } from 'react'
import { reportClientError } from '@/utils/errorReporting'

/**
 * 捕获前端未处理异常并异步上报。
 * 只提交错误摘要和运行环境，不读取 Prompt、AI 输出或本地凭证。
 */
export function ClientErrorReporter() {
  const reportedRef = useRef(new Map<string, number>())

  useEffect(() => {
    const report = (summary: string, errorType: string) => {
      const fingerprint = `${errorType}:${summary}`
      const now = Date.now()
      const lastReportedAt = reportedRef.current.get(fingerprint)

      // 同一错误短时间内只上报一次，避免异常风暴淹没开发者邮箱
      if (lastReportedAt && now - lastReportedAt < 30_000) return
      reportedRef.current.set(fingerprint, now)

      reportClientError({
        errorSummary: summary || '未知前端错误',
        errorType,
        stage: 'frontend',
      })
    }

    const handleError = (event: ErrorEvent) => {
      report(event.error instanceof Error ? event.error.message : event.message, 'UncaughtError')
    }

    const handleRejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason instanceof Error ? event.reason.message : 'UnhandledPromiseRejection'
      report(reason, 'UnhandledRejection')
    }

    window.addEventListener('error', handleError)
    window.addEventListener('unhandledrejection', handleRejection)
    return () => {
      window.removeEventListener('error', handleError)
      window.removeEventListener('unhandledrejection', handleRejection)
    }
  }, [])

  return null
}
