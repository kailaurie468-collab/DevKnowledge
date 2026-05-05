import { useState } from 'react'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { Layout } from '@/components/layout/Layout'
import { ParticleContext } from '@/stores/particleContext'
import { NotifyProvider } from '@/stores/notify'
import Antigravity from '@/components/Antigravity'
import { HomePage } from '@/pages/HomePage'
import { KnowledgePage } from '@/pages/KnowledgePage'
import { DemoPage } from '@/pages/DemoPage'
import { SkillsPage } from '@/pages/SkillsPage'
import { KbPage } from '@/pages/KbPage'
import { LoginPage } from '@/pages/LoginPage'
import { SettingsPage } from '@/pages/SettingsPage'

export default function App() {
  const [particleVisible, setParticleVisible] = useState(true)

  return (
    <ParticleContext.Provider value={{ visible: particleVisible, setVisible: setParticleVisible }}>
      <NotifyProvider>
      <div
        style={{
          position: 'fixed',
          inset: 0,
          zIndex: 0,
          pointerEvents: 'none',
          opacity: particleVisible ? 0.3 : 0,
          transition: 'opacity 0.4s ease',
        }}
      >
        <Antigravity
          count={200}
          magnetRadius={4}
          ringRadius={3}
          waveSpeed={0.4}
          waveAmplitude={0.5}
          particleSize={1.2}
          lerpSpeed={0.06}
          color="#cbc4e8"
          autoAnimate
          particleVariance={0.8}
          rotationSpeed={0.15}
          depthFactor={0.5}
          pulseSpeed={2}
          particleShape="capsule"
          fieldStrength={12}
        />
      </div>
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
      </NotifyProvider>
    </ParticleContext.Provider>
  )
}
