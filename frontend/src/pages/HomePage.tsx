import { useState, useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { gsap } from 'gsap'
import SplitText from '@/components/effects/SplitText'
import GradientText from '@/components/effects/GradientText'
import './HomePage.css'

const modules = [
  {
    path: '/knowledge',
    title: '知识搜索',
    desc: '搜索框架文档，跳转到具体知识点锚点。',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.3-4.3" />
        <path d="M11 8v6M8 11h6" />
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    accentLight: 'rgba(102, 126, 234, 0.12)',
    accentDark: 'rgba(102, 126, 234, 0.2)',
    iconBgLight: 'rgba(102, 126, 234, 0.1)',
    iconBgDark: 'rgba(102, 126, 234, 0.2)',
    glowColor: 'rgba(102, 126, 234, 0.3)',
    iconColor: '#667eea',
  },
  {
    path: '/demos',
    title: 'Demo 生成',
    desc: '通过 AI 生成代码示例及解释。',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <polyline points="16 18 22 12 16 6" />
        <polyline points="8 6 2 12 8 18" />
        <line x1="12" y1="2" x2="12" y2="22" opacity={0.3} />
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    accentLight: 'rgba(245, 87, 108, 0.12)',
    accentDark: 'rgba(245, 87, 108, 0.2)',
    iconBgLight: 'rgba(245, 87, 108, 0.1)',
    iconBgDark: 'rgba(245, 87, 108, 0.2)',
    glowColor: 'rgba(245, 87, 108, 0.3)',
    iconColor: '#f5576c',
  },
  {
    path: '/skills',
    title: 'Skills 构建',
    desc: '从描述中提取工作流，导出为 Claude Code Skills。',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 2L2 7l10 5 10-5-10-5z" />
        <path d="M2 17l10 5 10-5" />
        <path d="M2 12l10 5 10-5" />
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    accentLight: 'rgba(79, 172, 254, 0.12)',
    accentDark: 'rgba(79, 172, 254, 0.2)',
    iconBgLight: 'rgba(79, 172, 254, 0.1)',
    iconBgDark: 'rgba(79, 172, 254, 0.2)',
    glowColor: 'rgba(79, 172, 254, 0.3)',
    iconColor: '#4facfe',
  },
  {
    path: '/kb',
    title: '知识库',
    desc: '上传文档，构建 RAG 驱动的知识库。',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 19.5A2.5 2.5 0 016.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z" />
        <line x1="9" y1="7" x2="16" y2="7" />
        <line x1="9" y1="11" x2="14" y2="11" />
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    accentLight: 'rgba(67, 233, 123, 0.12)',
    accentDark: 'rgba(67, 233, 123, 0.2)',
    iconBgLight: 'rgba(67, 233, 123, 0.1)',
    iconBgDark: 'rgba(67, 233, 123, 0.2)',
    glowColor: 'rgba(67, 233, 123, 0.3)',
    iconColor: '#43e97b',
  },
  {
    path: '/wiki',
    title: 'Wiki 知识图谱',
    desc: 'LLM 驱动的知识图谱，自动构建实体关系。',
    icon: (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="5" r="3" />
        <circle cx="5" cy="19" r="3" />
        <circle cx="19" cy="19" r="3" />
        <line x1="12" y1="8" x2="5" y2="16" />
        <line x1="12" y1="8" x2="19" y2="16" />
        <line x1="5" y1="19" x2="19" y2="19" opacity={0.3} />
      </svg>
    ),
    gradient: 'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
    accentLight: 'rgba(250, 112, 154, 0.12)',
    accentDark: 'rgba(250, 112, 154, 0.2)',
    iconBgLight: 'rgba(250, 112, 154, 0.1)',
    iconBgDark: 'rgba(250, 112, 154, 0.2)',
    glowColor: 'rgba(250, 112, 154, 0.3)',
    iconColor: '#fa709a',
  },
]

export function HomePage() {
  const navigate = useNavigate()
  const [hovered, setHovered] = useState<string | null>(null)
  const [isDark, setIsDark] = useState(false)
  const cardsRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const checkDark = () => setIsDark(document.documentElement.classList.contains('dark'))
    checkDark()
    const observer = new MutationObserver(checkDark)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  // 卡片交错入场动画
  useEffect(() => {
    if (!cardsRef.current) return
    const cards = cardsRef.current.querySelectorAll('.home-module-card')
    gsap.fromTo(
      cards,
      { opacity: 0, y: 32, scale: 0.96 },
      {
        opacity: 1,
        y: 0,
        scale: 1,
        duration: 0.5,
        ease: 'power3.out',
        stagger: 0.08,
        delay: 0.4,
      }
    )
  }, [])

  return (
    <div className="home-page">
      {/* === Hero 标题区域 === */}
      <section className="home-hero">
        <SplitText
          text="DevKnowledge"
          tag="h1"
          className={`home-hero__title ${isDark ? 'text-gray-100' : 'text-gray-900'}`}
          delay={60}
          duration={0.5}
          ease="power3.out"
          splitType="chars"
          from={{ opacity: 0, y: 30, rotateX: -40 }}
          to={{ opacity: 1, y: 0, rotateX: 0 }}
          threshold={0}
          textAlign="center"
        />

        <SplitText
          text="开发者知识平台 — 搜索文档、生成 Demo、构建可复用 Skills"
          tag="p"
          className={`home-hero__subtitle ${isDark ? 'text-gray-400' : 'text-gray-500'}`}
          delay={20}
          duration={0.4}
          ease="power2.out"
          splitType="words"
          from={{ opacity: 0, y: 20 }}
          to={{ opacity: 1, y: 0 }}
          threshold={0}
          textAlign="center"
        />

        <div className="home-hero__tagline">
          <GradientText
            className="home-hero__gradient-text"
            colors={
              isDark
                ? ['#818cf8', '#c084fc', '#f472b6', '#818cf8']
                : ['#2563eb', '#7c3aed', '#db2777', '#2563eb']
            }
            animationSpeed={4}
            direction="horizontal"
          >
            致敬每一位坚持手搓的 coder
          </GradientText>
        </div>
      </section>

      {/* === 模块卡片区域 === */}
      <section className="home-modules" ref={cardsRef}>
        {modules.map((mod) => {
          const isHovered = hovered === mod.path
          return (
            <button
              key={mod.path}
              onClick={() => navigate(mod.path)}
              onMouseEnter={() => setHovered(mod.path)}
              onMouseLeave={() => setHovered(null)}
              className="home-module-card"
              style={{
                '--card-glow': mod.glowColor,
                '--card-accent': isDark ? mod.accentDark : mod.accentLight,
              } as React.CSSProperties}
            >
              {/* 顶部渐变装饰条 */}
              <div
                className="home-module-card__accent"
                style={{ background: mod.gradient }}
              />

              {/* 悬浮光晕 */}
              <div
                className="home-module-card__glow"
                style={{
                  opacity: isHovered ? 1 : 0,
                  background: `radial-gradient(circle at 50% 0%, ${mod.glowColor} 0%, transparent 70%)`,
                }}
              />

              {/* 图标 */}
              <div
                className="home-module-card__icon"
                style={{
                  background: isDark ? mod.iconBgDark : mod.iconBgLight,
                  color: mod.iconColor,
                }}
              >
                {mod.icon}
              </div>

              {/* 文字内容 */}
              <h2 className="home-module-card__title">{mod.title}</h2>
              <p className="home-module-card__desc">{mod.desc}</p>

              {/* 箭头指示器 */}
              <div className="home-module-card__arrow" style={{ opacity: isHovered ? 1 : 0 }}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                  <path d="M5 12h14M12 5l7 7-7 7" />
                </svg>
              </div>
            </button>
          )
        })}
      </section>
    </div>
  )
}
