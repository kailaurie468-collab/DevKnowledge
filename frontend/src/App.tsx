import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from '@/components/layout/Layout'
import { ParticleContext } from '@/stores/particleContext'
import { NotifyProvider } from '@/stores/notify'
import { HomePage } from '@/pages/HomePage'
import { KnowledgePage } from '@/pages/KnowledgePage'
import { DemoPage } from '@/pages/DemoPage'
import { SkillsPage } from '@/pages/SkillsPage'
import { KbPage } from '@/pages/KbPage'
import { LoginPage } from '@/pages/LoginPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { AiSettings } from '@/pages/settings/AiSettings'
import { EmbeddingSettings } from '@/pages/settings/EmbeddingSettings'
import { RerankerSettings } from '@/pages/settings/RerankerSettings'
import { StorageSettings } from '@/pages/settings/StorageSettings'
import { RagMetricsPage } from '@/pages/RagMetricsPage'
import { WikiPage } from '@/pages/WikiPage'

export default function App() {
  const [particleVisible, setParticleVisible] = useState(true)

  return (
    <ParticleContext.Provider value={{ visible: particleVisible, setVisible: setParticleVisible }}>
      <NotifyProvider>
      <div className="app-root">
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<Layout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/knowledge" element={<KnowledgePage />} />
            <Route path="/demos" element={<DemoPage />} />
            <Route path="/skills" element={<SkillsPage />} />
            <Route path="/kb" element={<KbPage />} />
            <Route path="/wiki" element={<WikiPage />} />

            {/* 设置嵌套路由 */}
            <Route path="/settings" element={<SettingsPage />}>
              <Route index element={<Navigate to="ai" replace />} />
              <Route path="ai" element={<AiSettings />} />
              <Route path="embedding" element={<EmbeddingSettings />} />
              <Route path="reranker" element={<RerankerSettings />} />
              <Route path="storage" element={<StorageSettings />} />
              <Route path="rag-metrics" element={<RagMetricsPage />} />
            </Route>
          </Route>
        </Routes>
      </BrowserRouter>
      </div>
      </NotifyProvider>
    </ParticleContext.Provider>
  )
}
