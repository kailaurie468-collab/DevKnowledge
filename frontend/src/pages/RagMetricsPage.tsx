import { RagMetrics } from './settings/RagMetrics'

/** RAG 指标独立页面 */
export function RagMetricsPage() {
  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">RAG 指标</h1>
      <RagMetrics />
    </div>
  )
}
