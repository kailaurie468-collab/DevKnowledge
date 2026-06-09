import { useState, useRef } from 'react'
import { wikiApi } from '@/api/wiki'
import type { WikiUploadResponse } from '@/types/wiki'

interface WikiUploadProps {
  onUploadSuccess: (result: WikiUploadResponse) => void
}

export function WikiUpload({ onUploadSuccess }: WikiUploadProps) {
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const vaultInputRef = useRef<HTMLInputElement>(null)

  // 单文件上传
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setError(null)

    try {
      const result = await wikiApi.uploadDocument(file)
      onUploadSuccess(result)
      if (fileInputRef.current) fileInputRef.current.value = ''
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  // 目录上传（Obsidian vault）
  const handleVaultSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files || [])
    if (files.length === 0) return

    setUploading(true)
    setError(null)

    try {
      const mdFiles = files.filter(f => f.name.endsWith('.md'))
      if (mdFiles.length === 0) {
        setError('目录中没有找到 .md 文件')
        return
      }
      const results = await wikiApi.uploadVault(mdFiles)
      results.forEach(r => onUploadSuccess(r))
      if (vaultInputRef.current) vaultInputRef.current.value = ''
    } catch (err) {
      setError(err instanceof Error ? err.message : '上传失败')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="space-y-2">
      <div className="flex space-x-2">
        {/* 单文件上传 */}
        <label className={`flex-1 flex items-center justify-center px-3 py-2 rounded-lg cursor-pointer transition-colors text-sm ${
          uploading
            ? 'bg-gray-400 cursor-not-allowed'
            : 'bg-primary-600 hover:bg-primary-700 text-white'
        }`}>
          <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          上传文档
          <input
            ref={fileInputRef}
            type="file"
            className="hidden"
            onChange={handleFileSelect}
            accept=".md,.txt,.pdf,.docx"
            disabled={uploading}
          />
        </label>

        {/* Vault 目录上传 */}
        <label className={`flex-1 flex items-center justify-center px-3 py-2 rounded-lg cursor-pointer transition-colors text-sm ${
          uploading
            ? 'bg-gray-400 cursor-not-allowed'
            : 'bg-purple-600 hover:bg-purple-700 text-white'
        }`}>
          <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
          </svg>
          上传 Vault
          <input
            ref={vaultInputRef}
            type="file"
            className="hidden"
            onChange={handleVaultSelect}
            // @ts-ignore - webkitdirectory 非标准属性
            webkitdirectory=""
            multiple
            disabled={uploading}
          />
        </label>
      </div>

      {uploading && (
        <div className="flex items-center text-sm text-gray-600">
          <svg className="animate-spin w-4 h-4 mr-2" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          正在处理...
        </div>
      )}

      {error && (
        <div className="text-sm text-red-600 bg-red-50 px-3 py-2 rounded">
          {error}
        </div>
      )}

      <p className="text-xs text-gray-500">
        支持单文件或 Obsidian vault 目录上传
      </p>
    </div>
  )
}
