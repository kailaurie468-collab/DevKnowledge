import { useRef, useState } from 'react'
import { useFrame } from '@react-three/fiber'
import { Html } from '@react-three/drei'
import * as THREE from 'three'

interface GraphNodeProps {
  position: [number, number, number]
  name: string
  type: string
  description?: string
  size?: number
  onClick?: () => void
}

const NODE_COLORS: Record<string, string> = {
  framework: '#3b82f6', // 蓝色
  concept: '#22c55e',   // 绿色
  api: '#f97316',       // 橙色
  tool: '#a855f7',      // 紫色
  default: '#6b7280',   // 灰色
}

export function GraphNode({ position, name, type, description, size = 0.5, onClick }: GraphNodeProps) {
  const meshRef = useRef<THREE.Mesh>(null)
  const [hovered, setHovered] = useState(false)
  const color = NODE_COLORS[type] || NODE_COLORS.default

  // 悬浮动画
  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.y += 0.01
      const targetScale = hovered ? 1.3 : 1
      meshRef.current.scale.lerp(
        new THREE.Vector3(targetScale, targetScale, targetScale),
        0.1
      )
    }
  })

  return (
    <group position={position}>
      <mesh
        ref={meshRef}
        onPointerOver={(e) => {
          e.stopPropagation()
          setHovered(true)
        }}
        onPointerOut={() => setHovered(false)}
        onClick={(e) => {
          e.stopPropagation()
          onClick?.()
        }}
      >
        <icosahedronGeometry args={[size, 1]} />
        <meshStandardMaterial
          color={color}
          emissive={hovered ? color : '#000000'}
          emissiveIntensity={hovered ? 0.5 : 0}
          roughness={0.3}
          metalness={0.7}
        />
      </mesh>

      {/* 悬浮标签 */}
      {hovered && (
        <Html distanceFactor={10} position={[0, size + 0.5, 0]}>
          <div className="bg-gray-900 text-white px-3 py-2 rounded-lg shadow-lg whitespace-nowrap pointer-events-none">
            <div className="font-bold text-sm">{name}</div>
            {description && (
              <div className="text-xs text-gray-300 max-w-[200px] truncate">{description}</div>
            )}
            <div className="text-xs mt-1" style={{ color }}>
              {type}
            </div>
          </div>
        </Html>
      )}
    </group>
  )
}
