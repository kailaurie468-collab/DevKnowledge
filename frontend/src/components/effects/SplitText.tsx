import { useRef, useEffect, useState } from 'react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'

gsap.registerPlugin(ScrollTrigger, useGSAP)

export interface SplitTextProps {
  text: string
  className?: string
  delay?: number
  duration?: number
  ease?: string
  splitType?: 'chars' | 'words' | 'lines'
  from?: gsap.TweenVars
  to?: gsap.TweenVars
  threshold?: number
  rootMargin?: string
  tag?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'p' | 'span'
  textAlign?: React.CSSProperties['textAlign']
  onLetterAnimationComplete?: () => void
}

const SplitText: React.FC<SplitTextProps> = ({
  text,
  className = '',
  delay = 50,
  duration = 0.6,
  ease = 'power3.out',
  splitType = 'chars',
  from = { opacity: 0, y: 40 },
  to = { opacity: 1, y: 0 },
  threshold = 0.1,
  rootMargin = '-100px',
  textAlign = 'center',
  tag = 'p',
  onLetterAnimationComplete,
}) => {
  const ref = useRef<HTMLParagraphElement>(null)
  const animationCompletedRef = useRef(false)
  const onCompleteRef = useRef(onLetterAnimationComplete)
  const [fontsLoaded, setFontsLoaded] = useState(false)

  useEffect(() => {
    onCompleteRef.current = onLetterAnimationComplete
  }, [onLetterAnimationComplete])

  useEffect(() => {
    if (document.fonts.status === 'loaded') {
      setFontsLoaded(true)
    } else {
      document.fonts.ready.then(() => setFontsLoaded(true))
    }
  }, [])

  useGSAP(
    () => {
      if (!ref.current || !text || !fontsLoaded) return
      if (animationCompletedRef.current) return

      const el = ref.current

      // 清理旧实例
      const existing = (el as any)._splitInstance
      if (existing) {
        try { existing.revert() } catch (_) {}
        ;(el as any)._splitInstance = null
      }

      // 按字符拆分
      const chars = text.split('')
      el.innerHTML = chars
        .map((char) =>
          char === ' '
            ? `<span class="split-char" style="display:inline-block">&nbsp;</span>`
            : `<span class="split-char" style="display:inline-block">${char}</span>`
        )
        .join('')

      const targets = el.querySelectorAll('.split-char')

      // 计算 ScrollTrigger start
      const startPct = (1 - threshold) * 100
      const marginMatch = /^(-?\d+(?:\.\d+)?)(px|em|rem|%)?$/.exec(rootMargin)
      const marginValue = marginMatch ? parseFloat(marginMatch[1]) : 0
      const marginUnit = marginMatch ? marginMatch[2] || 'px' : 'px'
      const sign =
        marginValue === 0
          ? ''
          : marginValue < 0
            ? `-=${Math.abs(marginValue)}${marginUnit}`
            : `+=${marginValue}${marginUnit}`
      const start = `top ${startPct}%${sign}`

      const tween = gsap.fromTo(
        targets,
        { ...from },
        {
          ...to,
          duration,
          ease,
          stagger: delay / 1000,
          scrollTrigger: {
            trigger: el,
            start,
            once: true,
            fastScrollEnd: true,
          },
          onComplete: () => {
            animationCompletedRef.current = true
            onCompleteRef.current?.()
          },
          willChange: 'transform, opacity',
          force3D: true,
        }
      )

      ;(el as any)._splitInstance = tween

      return () => {
        ScrollTrigger.getAll().forEach((st) => {
          if (st.trigger === el) st.kill()
        })
        tween.kill()
      }
    },
    {
      dependencies: [text, delay, duration, ease, splitType, JSON.stringify(from), JSON.stringify(to), threshold, rootMargin, fontsLoaded],
      scope: ref,
    }
  )

  const style: React.CSSProperties = {
    textAlign,
    overflow: 'hidden',
    display: 'inline-block',
    whiteSpace: 'normal',
    wordWrap: 'break-word',
    willChange: 'transform, opacity',
  }
  const classes = `split-parent ${className}`

  if (tag === 'h1') return <h1 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h1>
  if (tag === 'h2') return <h2 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h2>
  if (tag === 'h3') return <h3 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h3>
  if (tag === 'h4') return <h4 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h4>
  if (tag === 'h5') return <h5 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h5>
  if (tag === 'h6') return <h6 ref={ref as React.RefObject<HTMLHeadingElement>} style={style} className={classes}>{text}</h6>
  if (tag === 'span') return <span ref={ref as React.RefObject<HTMLSpanElement>} style={style} className={classes}>{text}</span>
  return <p ref={ref} style={style} className={classes}>{text}</p>
}

export default SplitText
