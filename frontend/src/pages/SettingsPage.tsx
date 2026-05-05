import { useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { AiSettings } from './settings/AiSettings'
import { StorageSettings } from './settings/StorageSettings'

type SettingsTab = 'ai' | 'storage'

const tabs: { key: SettingsTab; label: string }[] = [
  { key: 'ai', label: 'AI 配置' },
  { key: 'storage', label: '数据存储' },
]

export function SettingsPage() {
  const { isAuthenticated } = useAuthStore()
  const [activeTab, setActiveTab] = useState<SettingsTab>('ai')

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 mb-4">设置</h1>
        <p className="text-gray-500">请先登录以配置 AI 服务商。</p>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">设置</h1>

      {/* 顶部导航栏 */}
      <div className="flex gap-1 border-b border-gray-200 mb-6">
        {tabs.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-4 py-2 text-sm font-medium border-b-2 transition-colors -mb-px ${
              activeTab === tab.key
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* 子页面 */}
      {activeTab === 'ai' && <AiSettings />}
      {activeTab === 'storage' && <StorageSettings />}
    </div>
  )
}
