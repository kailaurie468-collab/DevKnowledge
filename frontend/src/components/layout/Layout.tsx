import { Outlet } from 'react-router-dom'
import { useParticleVisible } from '@/stores/particleContext'
import { Header } from './Header'
import { Sidebar } from './Sidebar'

export function Layout() {
  const { setVisible } = useParticleVisible()

  return (
    <div className="h-screen flex flex-col">
      <Header />
      <div className="flex flex-1 overflow-hidden">
        <div
          onMouseEnter={() => setVisible(false)}
          onMouseLeave={() => setVisible(true)}
        >
          <Sidebar />
        </div>
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
