import { useState, useEffect } from 'react'
import { settingsApi } from '@/api/settings'
import type { RagMetric } from '@/types/api'

export function RagMetrics() {
  const [metrics, setMetrics] = useState<RagMetric[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    settingsApi.getRagMetrics()
      .then(setMetrics)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  // 计算概览指标
  const ragUsedMetrics = metrics.filter(m => m.ragUsed)
  const avgSimilarity = ragUsedMetrics.length > 0
    ? ragUsedMetrics.reduce((sum, m) => sum + (m.avgSimilarity || 0), 0) / ragUsedMetrics.length
    : 0
  const avgRetrievalMs = ragUsedMetrics.length > 0
    ? ragUsedMetrics.reduce((sum, m) => sum + (m.retrievalMs || 0), 0) / ragUsedMetrics.length
    : 0
  const ragUsageRate = metrics.length > 0
    ? (ragUsedMetrics.length / metrics.length) * 100
    : 0

  // 按日期聚合相似度（用于柱状图）
  const dailySimilarity = new Map<string, number[]>()
  ragUsedMetrics.forEach(m => {
    const date = m.createdAt.slice(0, 10)
    if (!dailySimilarity.has(date)) dailySimilarity.set(date, [])
    dailySimilarity.get(date)!.push(m.avgSimilarity || 0)
  })
  const chartData = Array.from(dailySimilarity.entries())
    .map(([date, values]) => ({
      date,
      avg: values.reduce((a, b) => a + b, 0) / values.length,
    }))
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(-7)
  const maxChartValue = Math.max(...chartData.map(d => d.avg), 0.01)

  if (loading) {
    return <p className="text-gray-400 dark:text-gray-500 text-sm py-8 text-center">加载中...</p>
  }

  return (
    <div>
      <h2 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-6">RAG 检索指标</h2>

      {/* 概览卡片 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800">
          <p className="text-xs text-gray-500 dark:text-gray-400">平均检索相似度</p>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1">
            {(avgSimilarity * 100).toFixed(1)}%
          </p>
        </div>
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800">
          <p className="text-xs text-gray-500 dark:text-gray-400">平均检索耗时</p>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1">
            {avgRetrievalMs.toFixed(0)}ms
          </p>
        </div>
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 bg-white dark:bg-gray-800">
          <p className="text-xs text-gray-500 dark:text-gray-400">RAG 使用率</p>
          <p className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-1">
            {ragUsageRate.toFixed(0)}%
          </p>
        </div>
      </div>

      {/* 相似度趋势图 */}
      <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-6 mb-6 bg-white dark:bg-gray-800">
        <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-4">相似度趋势（近 7 天）</h3>
        {chartData.length === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500 py-8 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-40">
            {chartData.map((d, i) => {
              const height = maxChartValue > 0 ? (d.avg / maxChartValue) * 100 : 0
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 group">
                  <span className="text-xs text-gray-400 dark:text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity">
                    {(d.avg * 100).toFixed(1)}%
                  </span>
                  <div className="w-full flex items-end" style={{ height: '120px' }}>
                    <div
                      className="w-full bg-green-500 rounded-t transition-all group-hover:bg-green-600"
                      style={{ height: `${Math.max(height, 2)}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-500 dark:text-gray-400">{d.date.slice(5)}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 检索详情表格 */}
      <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-6 bg-white dark:bg-gray-800">
        <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-4">检索详情（最近 20 条）</h3>
        {ragUsedMetrics.length === 0 ? (
          <p className="text-sm text-gray-400 dark:text-gray-500 py-8 text-center">暂无 RAG 检索记录</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 dark:text-gray-400 border-b border-gray-200 dark:border-gray-700">
                  <th className="pb-2 pr-4">Demo</th>
                  <th className="pb-2 pr-4">top-K</th>
                  <th className="pb-2 pr-4">命中数</th>
                  <th className="pb-2 pr-4">相似度</th>
                  <th className="pb-2 pr-4">耗时</th>
                  <th className="pb-2">工具调用</th>
                </tr>
              </thead>
              <tbody>
                {ragUsedMetrics.slice(0, 20).map((m, i) => (
                  <tr key={i} className="border-b border-gray-100 dark:border-gray-700">
                    <td className="py-2 pr-4 max-w-[200px] truncate text-gray-900 dark:text-gray-100">{m.demoTitle}</td>
                    <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{m.topK}</td>
                    <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{m.chunkCount}</td>
                    <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{((m.avgSimilarity || 0) * 100).toFixed(1)}%</td>
                    <td className="py-2 pr-4 text-gray-700 dark:text-gray-300">{m.retrievalMs}ms</td>
                    <td className="py-2 text-gray-700 dark:text-gray-300">{m.toolCallCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
