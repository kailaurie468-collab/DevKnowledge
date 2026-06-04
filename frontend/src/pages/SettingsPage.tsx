import { useState } from 'react'
import { useAuthStore } from '@/stores/authStore'
import { AiSettings } from './settings/AiSettings'
import { EmbeddingSettings } from './settings/EmbeddingSettings'
import { StorageSettings } from './settings/StorageSettings'

type SettingsTab = 'ai' | 'embedding' | 'storage'

const tabs: { key: SettingsTab; label: string; desc: string }[] = [
  { key: 'ai', label: 'AI 服务配置', desc: 'Chat 模型配置' },
  { key: 'embedding', label: 'Embedding AI', desc: '文本向量化模型' },
  { key: 'storage', label: '数据存储', desc: '本地存储设置' },
]

export function SettingsPage() {
  const { isAuthenticated } = useAuthStore()
  const [activeTab, setActiveTab] = useState<SettingsTab>('ai')

  if (!isAuthenticated) {
    return (
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-4">设置</h1>
        <p className="text-gray-500 dark:text-gray-400">请先登录以配置服务。</p>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">设置</h1>

      <div className="flex gap-6">
        {/* 侧边栏 */}
        <nav className="w-48 flex-shrink-0">
          <div className="space-y-1">
            {tabs.map(tab => (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-colors ${
                  activeTab === tab.key
                    ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400 font-medium'
                    : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-gray-900 dark:hover:text-gray-200'
                }`}
              >
                <div>{tab.label}</div>
                <div className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">{tab.desc}</div>
              </button>
            ))}
          </div>
        </nav>

        {/* 内容区 */}
        <div className="flex-1 min-w-0">
          {activeTab === 'ai' && <AiSettings />}
          {activeTab === 'embedding' && <EmbeddingSettings />}
          {activeTab === 'storage' && <StorageSettings />}
        </div>
      </div>
    </div>
  )
}
