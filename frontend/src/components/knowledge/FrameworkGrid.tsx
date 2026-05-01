import type { Framework } from '@/types/api'

interface FrameworkGridProps {
  frameworks: Framework[]
  onSelect: (framework: Framework) => void
}

const categoryColors: Record<string, string> = {
  frontend: 'bg-blue-100 text-blue-700',
  backend: 'bg-green-100 text-green-700',
  mobile: 'bg-purple-100 text-purple-700',
}

const categoryLabels: Record<string, string> = {
  frontend: '前端',
  backend: '后端',
  mobile: '移动端',
}

export function FrameworkGrid({ frameworks, onSelect }: FrameworkGridProps) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {frameworks.map(fw => (
        <button
          key={fw.id}
          onClick={() => onSelect(fw)}
          className="text-left p-4 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
        >
          <div className="flex items-center gap-3 mb-2">
            {fw.iconUrl ? (
              <img src={fw.iconUrl} alt={fw.name} className="w-8 h-8 rounded" />
            ) : (
              <div className="w-8 h-8 rounded bg-gray-200 flex items-center justify-center text-sm font-bold text-gray-500">
                {fw.name[0]}
              </div>
            )}
            <div>
              <h3 className="font-medium text-gray-900">{fw.name}</h3>
              <span className={`text-xs px-2 py-0.5 rounded-full ${categoryColors[fw.category] || 'bg-gray-100 text-gray-600'}`}>
                {categoryLabels[fw.category] || fw.category}
              </span>
            </div>
          </div>
          {fw.description && (
            <p className="text-sm text-gray-500 line-clamp-2">{fw.description}</p>
          )}
        </button>
      ))}
    </div>
  )
}
