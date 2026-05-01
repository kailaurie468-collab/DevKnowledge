import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from '@/components/layout/Layout'
import { HomePage } from '@/pages/HomePage'
import { KnowledgePage } from '@/pages/KnowledgePage'
import { DemoPage } from '@/pages/DemoPage'
import { SkillsPage } from '@/pages/SkillsPage'
import { KbPage } from '@/pages/KbPage'
import { LoginPage } from '@/pages/LoginPage'
import { SettingsPage } from '@/pages/SettingsPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<Layout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/knowledge" element={<KnowledgePage />} />
          <Route path="/demos" element={<DemoPage />} />
          <Route path="/skills" element={<SkillsPage />} />
          <Route path="/kb" element={<KbPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
