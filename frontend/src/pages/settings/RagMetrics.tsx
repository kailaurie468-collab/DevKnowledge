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
          <div className="relative h-40 mt-4">
            <svg className="w-full h-full overflow-visible" preserveAspectRatio="none">
              {/* 绘制折线 */}
              <polyline
                fill="none"
                stroke="#10b981"
                strokeWidth="3"
                strokeLinecap="round"
                strokeLinejoin="round"
                points={chartData.map((d, i) => {
                  const x = (i / Math.max(chartData.length - 1, 1)) * 100;
                  const y = maxChartValue > 0 ? 100 - (d.avg / maxChartValue) * 100 : 100;
                  return `${x}%,${y}%`;
                }).join(' ')}
              />
              {/* 绘制数据点 */}
              {chartData.map((d, i) => {
                const x = (i / Math.max(chartData.length - 1, 1)) * 100;
                const y = maxChartValue > 0 ? 100 - (d.avg / maxChartValue) * 100 : 100;
                return (
                  <g key={i} className="group">
                    <circle cx={`${x}%`} cy={`${y}%`} r="4" fill="#10b981" className="cursor-pointer transition-all group-hover:r-6" />
                    {/* Tooltip (Hover 时显示) */}
                    <text
                      x={`${x}%`}
                      y={`${y - 15}%`}
                      textAnchor="middle"
                      className="text-xs fill-gray-500 dark:fill-gray-400 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none"
                    >
                      {(d.avg * 100).toFixed(1)}%
                    </text>
                  </g>
                );
              })}
            </svg>
            {/* 绘制 X 轴标签 */}
            <div className="absolute -bottom-6 left-0 right-0 flex justify-between">
              {chartData.map((d, i) => (
                <span key={i} className="text-xs text-gray-500 dark:text-gray-400">
                  {d.date.slice(5)}
                </span>
              ))}
            </div>
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
