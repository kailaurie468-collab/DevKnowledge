import { useMemo, useCallback } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { GraphNode } from './GraphNode'
import type { WikiGraphData } from '@/types/wiki'

interface WikiGraph3DProps {
  data: WikiGraphData
  onNodeClick?: (entityId: string, pagePath?: string) => void
}

// 圆形布局算法
function calculateLayout(data: WikiGraphData): Map<string, [number, number, number]> {
  const positions = new Map<string, [number, number, number]>()
  const entityCount = data.entities.length
  if (entityCount === 0) return positions

  data.entities.forEach((entity, index) => {
    const angle = (index / entityCount) * Math.PI * 2
    const radius = Math.max(5, entityCount * 0.8)
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    const y = (Math.sin(index * 1.5) * 2) // 波浪高度
    positions.set(entity.id, [x, y, z])
  })

  return positions
}

export function WikiGraph3D({ data, onNodeClick }: WikiGraph3DProps) {
  const positions = useMemo(() => calculateLayout(data), [data])

  // 计算节点大小（根据关系数量）
  const nodeSizes = useMemo(() => {
    const sizes = new Map<string, number>()
    const relationCounts = new Map<string, number>()

    data.relations.forEach(rel => {
      relationCounts.set(rel.sourceId, (relationCounts.get(rel.sourceId) || 0) + 1)
      relationCounts.set(rel.targetId, (relationCounts.get(rel.targetId) || 0) + 1)
    })

    data.entities.forEach(entity => {
      const count = relationCounts.get(entity.id) || 0
      sizes.set(entity.id, 0.3 + count * 0.1)
    })

    return sizes
  }, [data])

  const handleNodeClick = useCallback((entityId: string) => {
    const entity = data.entities.find(e => e.id === entityId)
    if (entity) {
      onNodeClick?.(entityId, entity.pagePath)
    }
  }, [data, onNodeClick])

  // 构建边的几何数据
  const edgePositions = useMemo(() => {
    const positionsArray: number[] = []
    data.relations.forEach(rel => {
      const sourcePos = positions.get(rel.sourceId)
      const targetPos = positions.get(rel.targetId)
      if (sourcePos && targetPos) {
        positionsArray.push(...sourcePos, ...targetPos)
      }
    })
    return new Float32Array(positionsArray)
  }, [data, positions])

  return (
    <div className="w-full h-full">
      <Canvas camera={{ position: [0, 5, 15], fov: 60 }}>
        <ambientLight intensity={0.5} />
        <pointLight position={[10, 10, 10]} intensity={1} />
        <pointLight position={[-10, -10, -10]} intensity={0.5} />

        {/* 渲染边 */}
        {edgePositions.length > 0 && (
          <lineSegments>
            <bufferGeometry>
              <bufferAttribute
                attach="attributes-position"
                args={[edgePositions, 3]}
              />
            </bufferGeometry>
            <lineBasicMaterial color="#ffffff" opacity={0.3} transparent />
          </lineSegments>
        )}

        {/* 渲染节点 */}
        {data.entities.map(entity => {
          const pos = positions.get(entity.id)
          if (!pos) return null

          return (
            <GraphNode
              key={entity.id}
              position={pos}
              name={entity.name}
              type={entity.type}
              description={entity.description}
              size={nodeSizes.get(entity.id)}
              onClick={() => handleNodeClick(entity.id)}
            />
          )
        })}

        <OrbitControls enableDamping dampingFactor={0.05} />
      </Canvas>
    </div>
  )
}
