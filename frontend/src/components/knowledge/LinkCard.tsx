import type { LinkSearchResult } from '@/types/api'

interface LinkCardProps {
  result: LinkSearchResult
}

export function LinkCard({ result }: LinkCardProps) {
  const { link, frameworkName } = result
  const fullUrl = link.anchor ? `${link.url}#${link.anchor}` : link.url

  return (
    <a
      href={fullUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="block p-4 border border-gray-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all"
    >
      <div className="flex items-start justify-between gap-2 mb-1">
        <h3 className="font-medium text-gray-900 text-sm">{link.title}</h3>
        <span className="text-xs text-gray-400 shrink-0">{frameworkName}</span>
      </div>
      {link.description && (
        <p className="text-sm text-gray-500 line-clamp-2 mb-2">{link.description}</p>
      )}
      {link.tags.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {link.tags.map(tag => (
            <span
              key={tag}
              className="text-xs px-1.5 py-0.5 bg-gray-100 text-gray-600 rounded"
            >
              {tag}
            </span>
          ))}
        </div>
      )}
    </a>
  )
}
