import { createContext, useContext, useState, useCallback, useRef } from 'react'
import type { ReactNode } from 'react'

interface Notify {
  id: number
  type: 'error' | 'success' | 'info' | 'warning'
  message: string
}

interface NotifyContextValue {
  notify: (message: string, type?: Notify['type']) => void
}

const NotifyContext = createContext<NotifyContextValue>({ notify: () => {} })

export function useNotify() {
  return useContext(NotifyContext)
}

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [notices, setNotices] = useState<Notify[]>([])
  const nextId = useRef(0)

  const notify = useCallback((message: string, type: Notify['type'] = 'error') => {
    const id = nextId.current++
    setNotices(prev => [...prev, { id, type, message }])
    setTimeout(() => {
      setNotices(prev => prev.filter(n => n.id !== id))
    }, 4000)
  }, [])

  return (
    <NotifyContext.Provider value={{ notify }}>
      {children}
      {/* 通知弹窗 */}
      <div className="fixed top-4 right-4 z-50 space-y-2" style={{ pointerEvents: 'none' }}>
        {notices.map(n => (
          <div
            key={n.id}
            className={`px-4 py-3 rounded-lg shadow-lg text-sm font-medium max-w-sm animate-slide-in ${
              n.type === 'error' ? 'bg-red-600 text-white' :
              n.type === 'success' ? 'bg-green-600 text-white' :
              n.type === 'warning' ? 'bg-amber-500 text-white' :
              'bg-gray-800 text-white'
            }`}
            style={{ pointerEvents: 'auto' }}
          >
            {n.message}
          </div>
        ))}
      </div>
    </NotifyContext.Provider>
  )
}
