import { useRef, useEffect, useState, useCallback } from 'react'

interface LiquidGlassProps {
  containerRef: React.RefObject<HTMLElement | null>
  activeSelector?: string
  blur?: number
  duration?: number
}

/**
 * iOS 风格液态玻璃效果（支持明暗主题）
 */
export function LiquidGlass({
  containerRef,
  activeSelector = '.active',
  blur = 20,
  duration = 400,
}: LiquidGlassProps) {
  const glassRef = useRef<HTMLDivElement>(null)
  const [style, setStyle] = useState<React.CSSProperties>({ opacity: 0 })
  const [isDark, setIsDark] = useState(false)

  // 监听暗色模式
  useEffect(() => {
    const checkDark = () => setIsDark(document.documentElement.classList.contains('dark'))
    checkDark()
    const observer = new MutationObserver(checkDark)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  const updatePosition = useCallback(() => {
    const container = containerRef.current
    const glass = glassRef.current
    if (!container || !glass) return

    const activeEl = container.querySelector(activeSelector)
    if (!activeEl) {
      setStyle(prev => ({ ...prev, opacity: 0 }))
      return
    }

    const containerRect = container.getBoundingClientRect()
    const activeRect = activeEl.getBoundingClientRect()
    const top = activeRect.top - containerRect.top
    const width = activeRect.width
    const height = activeRect.height

    setStyle({
      opacity: 1,
      transform: `translateY(${top}px)`,
      width: `${width}px`,
      height: `${height}px`,
      transition: `all ${duration}ms cubic-bezier(0.22, 1, 0.36, 1)`,
    })
  }, [containerRef, activeSelector, duration])

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    const observer = new MutationObserver(() => {
      requestAnimationFrame(updatePosition)
    })
    observer.observe(container, {
      attributes: true,
      attributeFilter: ['class'],
      subtree: true,
    })

    const resizeObserver = new ResizeObserver(() => {
      requestAnimationFrame(updatePosition)
    })
    resizeObserver.observe(container)

    updatePosition()

    return () => {
      observer.disconnect()
      resizeObserver.disconnect()
    }
  }, [containerRef, updatePosition])

  return (
    <div
      ref={glassRef}
      className="absolute left-0.5 right-0.5 pointer-events-none"
      style={{
        ...style,
        backdropFilter: `blur(${blur}px) saturate(200%) brightness(${isDark ? 1.3 : 1.1})`,
        WebkitBackdropFilter: `blur(${blur}px) saturate(200%) brightness(${isDark ? 1.3 : 1.1})`,
        background: isDark
          ? `linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(59, 130, 246, 0.12) 50%, rgba(99, 102, 241, 0.15) 100%)`
          : `linear-gradient(135deg, rgba(255,255,255,0.45) 0%, rgba(255,255,255,0.2) 40%, rgba(255,255,255,0.35) 100%)`,
        borderRadius: '0.5rem',
        boxShadow: isDark
          ? `0 2px 8px rgba(0,0,0,0.3), 0 8px 24px rgba(0,0,0,0.2), 0 0 0 1px rgba(99,102,241,0.3), inset 0 1px 0 rgba(255,255,255,0.1)`
          : `0 2px 8px rgba(0,0,0,0.08), 0 8px 24px rgba(0,0,0,0.06), 0 0 0 1px rgba(255,255,255,0.5), inset 0 1px 0 rgba(255,255,255,0.7), inset 0 -1px 0 rgba(0,0,0,0.04)`,
        border: isDark
          ? '1px solid rgba(99, 102, 241, 0.3)'
          : '1px solid rgba(255, 255, 255, 0.5)',
      }}
    >
      {/* 高光 */}
      <div
        className="absolute inset-0 rounded-lg overflow-hidden pointer-events-none"
        style={{
          background: isDark
            ? `radial-gradient(ellipse at 20% 15%, rgba(129,140,248,0.2) 0%, transparent 60%)`
            : `radial-gradient(ellipse at 20% 15%, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.15) 40%, transparent 65%)`,
        }}
      />
      {/* 折射线 */}
      <div
        className="absolute top-0.5 left-3 right-3 h-px pointer-events-none"
        style={{
          background: isDark
            ? 'linear-gradient(90deg, transparent, rgba(129,140,248,0.4), transparent)'
            : 'linear-gradient(90deg, transparent, rgba(255,255,255,0.8), transparent)',
        }}
      />
      <div
        className="absolute bottom-0.5 left-3 right-3 h-px pointer-events-none"
        style={{
          background: isDark
            ? 'linear-gradient(90deg, transparent, rgba(129,140,248,0.2), transparent)'
            : 'linear-gradient(90deg, transparent, rgba(255,255,255,0.4), transparent)',
        }}
      />
    </div>
  )
}
