import { Canvas, useFrame, useThree } from '@react-three/fiber'
import React, { useEffect, useMemo, useRef } from 'react'
import * as THREE from 'three'

interface AntigravityProps {
  count?: number
  magnetRadius?: number
  ringRadius?: number
  waveSpeed?: number
  waveAmplitude?: number
  particleSize?: number
  lerpSpeed?: number
  color?: string
  autoAnimate?: boolean
  particleVariance?: number
  rotationSpeed?: number
  depthFactor?: number
  pulseSpeed?: number
  particleShape?: 'capsule' | 'sphere' | 'box' | 'tetrahedron'
  fieldStrength?: number
}

const AntigravityInner: React.FC<AntigravityProps> = ({
  count = 300,
  magnetRadius = 10,
  ringRadius = 10,
  waveSpeed = 0.4,
  waveAmplitude = 1,
  particleSize = 2,
  lerpSpeed = 0.1,
  color = '#FF9FFC',
  autoAnimate = false,
  particleVariance = 1,
  rotationSpeed = 0,
  depthFactor = 1,
  pulseSpeed = 3,
  particleShape = 'capsule',
  fieldStrength = 10,
}) => {
  const meshRef = useRef<THREE.InstancedMesh>(null)
  const { viewport } = useThree()
  const dummy = useMemo(() => new THREE.Object3D(), [])

  const mouseNorm = useRef({ x: 0, y: 0 })
  const lastMouseMoveTime = useRef(0)
  const virtualMouse = useRef({ x: 0, y: 0 })

  // 全局 mousemove 追踪，Canvas pointer-events:none 时仍能跟随鼠标
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      mouseNorm.current.x = (e.clientX / window.innerWidth) * 2 - 1
      mouseNorm.current.y = -(e.clientY / window.innerHeight) * 2 + 1
      lastMouseMoveTime.current = Date.now()
    }
    window.addEventListener('mousemove', onMove)
    return () => window.removeEventListener('mousemove', onMove)
  }, [])

  // 缩小散布范围：用 viewport 的一部分
  const spreadScale = 0.5

  const particles = useMemo(() => {
    const temp = []
    const width = (viewport.width || 100) * spreadScale
    const height = (viewport.height || 100) * spreadScale

    for (let i = 0; i < count; i++) {
      const t = Math.random() * 100
      const speed = 0.01 + Math.random() / 200

      const x = (Math.random() - 0.5) * width
      const y = (Math.random() - 0.5) * height
      const z = (Math.random() - 0.5) * 10

      const randomRadiusOffset = (Math.random() - 0.5) * 2

      temp.push({
        t,
        speed,
        mx: x,
        my: y,
        mz: z,
        cx: x,
        cy: y,
        cz: z,
        randomRadiusOffset,
      })
    }
    return temp
  }, [count, viewport.width, viewport.height])

  useFrame((state) => {
    const mesh = meshRef.current
    if (!mesh) return

    const { viewport: v } = state
    const norm = mouseNorm.current

    let destX = (norm.x * v.width) / 2
    let destY = (norm.y * v.height) / 2

    if (autoAnimate && Date.now() - lastMouseMoveTime.current > 2000) {
      const time = state.clock.getElapsedTime()
      destX = Math.sin(time * 0.5) * (v.width / 4)
      destY = Math.cos(time * 0.5 * 2) * (v.height / 4)
    }

    const smoothFactor = 0.05
    virtualMouse.current.x += (destX - virtualMouse.current.x) * smoothFactor
    virtualMouse.current.y += (destY - virtualMouse.current.y) * smoothFactor

    const targetX = virtualMouse.current.x
    const targetY = virtualMouse.current.y

    const globalRotation = state.clock.getElapsedTime() * rotationSpeed

    particles.forEach((particle, i) => {
      const { mx, my, mz, cz, randomRadiusOffset } = particle

      particle.t += particle.speed / 2

      const projectionFactor = 1 - cz / 50
      const projectedTargetX = targetX * projectionFactor
      const projectedTargetY = targetY * projectionFactor

      const dx = mx - projectedTargetX
      const dy = my - projectedTargetY
      const dist = Math.sqrt(dx * dx + dy * dy)

      const targetPos = { x: mx, y: my, z: mz * depthFactor }

      if (dist < magnetRadius) {
        const angle = Math.atan2(dy, dx) + globalRotation

        const wave =
          Math.sin(particle.t * waveSpeed + angle) * (0.5 * waveAmplitude)
        const deviation = randomRadiusOffset * (5 / (fieldStrength + 0.1))

        const currentRingRadius = ringRadius + wave + deviation

        targetPos.x = projectedTargetX + currentRingRadius * Math.cos(angle)
        targetPos.y = projectedTargetY + currentRingRadius * Math.sin(angle)
        targetPos.z =
          mz * depthFactor +
          Math.sin(particle.t) * (1 * waveAmplitude * depthFactor)
      }

      particle.cx += (targetPos.x - particle.cx) * lerpSpeed
      particle.cy += (targetPos.y - particle.cy) * lerpSpeed
      particle.cz += (targetPos.z - particle.cz) * lerpSpeed

      dummy.position.set(particle.cx, particle.cy, particle.cz)

      dummy.lookAt(projectedTargetX, projectedTargetY, particle.cz)
      dummy.rotateX(Math.PI / 2)

      const currentDistToMouse = Math.sqrt(
        Math.pow(particle.cx - projectedTargetX, 2) +
          Math.pow(particle.cy - projectedTargetY, 2),
      )

      const distFromRing = Math.abs(currentDistToMouse - ringRadius)
      let scaleFactor = 1 - distFromRing / 10
      scaleFactor = Math.max(0, Math.min(1, scaleFactor))

      const finalScale =
        scaleFactor *
        (0.8 + Math.sin(particle.t * pulseSpeed) * 0.2 * particleVariance) *
        particleSize
      dummy.scale.set(finalScale, finalScale, finalScale)

      dummy.updateMatrix()

      mesh.setMatrixAt(i, dummy.matrix)
    })

    mesh.instanceMatrix.needsUpdate = true
  })

  return (
    <instancedMesh ref={meshRef} args={[undefined, undefined, count]}>
      {particleShape === 'capsule' && <capsuleGeometry args={[0.1, 0.4, 4, 8]} />}
      {particleShape === 'sphere' && <sphereGeometry args={[0.2, 16, 16]} />}
      {particleShape === 'box' && <boxGeometry args={[0.3, 0.3, 0.3]} />}
      {particleShape === 'tetrahedron' && <tetrahedronGeometry args={[0.3]} />}
      <meshBasicMaterial color={color} />
    </instancedMesh>
  )
}

const Antigravity: React.FC<AntigravityProps> = (props) => {
  return (
    <Canvas
      camera={{ position: [0, 0, 50], fov: 35 }}
      gl={{ alpha: true }}
      style={{ background: 'transparent', pointerEvents: 'none' }}
    >
      <AntigravityInner {...props} />
    </Canvas>
  )
}

export default Antigravity
