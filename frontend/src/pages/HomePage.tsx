import { useState, useEffect } from 'react'
import { createPortal } from 'react-dom'
import SplitText from '@/components/effects/SplitText'
import GradientText from '@/components/effects/GradientText'
// import CircularGallery from '@/components/effects/CircularGallery'
import FloatingLines from '@/components/effects/FloatingLines'
import DotGrid from '@/components/effects/DotGrid'
import './HomePage.css'

export function HomePage() {
  const [isDark, setIsDark] = useState(false)

  useEffect(() => {
    const checkDark = () => setIsDark(document.documentElement.classList.contains('dark'))
    checkDark()
    const observer = new MutationObserver(checkDark)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  // const galleryItems = [
  //   { image: '/images/gallery/demo_generation.png', text: 'Demo生成' },
  //   { image: '/images/gallery/skills_building.png', text: 'skill构建' },
  //   { image: '/images/gallery/knowledge_base.png', text: '知识库' },
  //   { image: '/images/gallery/wiki_graph.png', text: 'wiki知识图谱' },
  // ]

  return (
    <div className="home-wrapper relative w-full h-full flex flex-col">
      {/* 背景层：暗色模式用 FloatingLines，亮色模式用 DotGrid */}
      {createPortal(
        <div style={{ position: 'fixed', top: 0, left: 0, width: '100vw', height: '100vh', zIndex: -1, pointerEvents: 'none', background: isDark ? '#0a0a1a' : 'transparent' }}>
          {isDark ? (
            <DotGrid
              dotSize={10}
              gap={15}
              baseColor="#4338ca"
              activeColor="#818cf8"
              proximity={120}
              shockRadius={250}
              shockStrength={5}
              resistance={750}
              returnDuration={1.5}
            />
          ) : (
            <FloatingLines
              linesGradient={["#2563eb", "#7c3aed", "#db2777"]}
              enabledWaves={['top', 'middle', 'bottom']}
              lineCount={[15, 20, 15]}
              lineDistance={[8, 6, 8]}
              bendRadius={4.0}
              bendStrength={-0.6}
              parallax={true}
              interactive={true}
            />
          )}
        </div>,
        document.body
      )}

      <div className="home-page" style={{ position: 'relative', zIndex: 1 }}>
        {/* === Hero 标题区域 === */}
        <section className="home-hero" style={{ flexShrink: 0 }}>
          <SplitText
            text="DevKnowledge"
            tag="h1"
            className="home-hero__title"
            delay={50}
            from={{ opacity: 0, transform: 'translate3d(0,40px,0)' }}
            to={{ opacity: 1, transform: 'translate3d(0,0,0)' }}
            ease="power3.out"
            threshold={0.2}
            rootMargin="-50px"
          />
          <p className="home-hero__subtitle">
            让知识不再遥远，让开发更有温度
          </p>
          <div className="home-hero__tagline">
            <GradientText
              className="home-hero__gradient-text"
              colors={
                isDark
                  ? ['#818cf8', '#c084fc', '#f472b6', '#818cf8']
                  : ['#f6f9f6', '#82ed3a', '#db2777', '#ebd125']
              }
              animationSpeed={4}
              direction="horizontal"
            >
              致敬每一位坚持手搓的 coder
            </GradientText>
          </div>
        </section>

        {/* === 模块画廊区域（已注释） ===
        <section style={{ flex: 1, position: 'relative', minHeight: 0 }}>
          <CircularGallery
            bend={3}
            textColor="#ffffff"
            borderRadius={0.05}
            scrollEase={0.02}
            items={galleryItems}
          />
        </section>
        */}
      </div>
    </div>
  )
}
