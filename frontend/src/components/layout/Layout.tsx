import { Outlet } from 'react-router-dom'
import { useParticleVisible } from '@/stores/particleContext'
import { Header } from './Header'
import { Sidebar } from './Sidebar'

export function Layout() {
  const { setVisible } = useParticleVisible()

  return (
    <div className="h-screen flex flex-col bg-white dark:bg-gray-950 transition-colors">
      <Header />
      <div className="flex flex-1 overflow-hidden">
        <div
          onMouseEnter={() => setVisible(false)}
          onMouseLeave={() => setVisible(true)}
        >
          <Sidebar />
        </div>
        <main className="flex-1 overflow-auto p-6 bg-white dark:bg-gray-950 transition-colors">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
