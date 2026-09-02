import { useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { telemetryApi } from '@/api/telemetry'
import { useNotify } from '@/stores/notify'

const FEEDBACK_TYPES = [
  { value: 'FEATURE', label: '功能建议' },
  { value: 'EXPERIENCE', label: '体验问题' },
  { value: 'INACCURATE', label: '结果不准确' },
  { value: 'OTHER', label: '其他' },
]

/**
 * 全局用户反馈入口。
 * 用户只看到反馈表单，内部邮件和开发者后台实现对用户透明。
 */
export function FeedbackDialog() {
  const { notify } = useNotify()
  const [open, setOpen] = useState(false)
  const [feedbackType, setFeedbackType] = useState(FEEDBACK_TYPES[0].value)
  const [content, setContent] = useState('')
  const [contact, setContact] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!content.trim()) {
      notify('请填写反馈内容', 'warning')
      return
    }

    setSubmitting(true)
    try {
      await telemetryApi.submitFeedback({
        feedbackType,
        content: content.trim(),
        contact: contact.trim() || undefined,
        page: window.location.pathname,
      })
      notify('感谢反馈，我们会认真查看', 'success')
      setContent('')
      setContact('')
      setOpen(false)
    } catch (error) {
      notify(error instanceof Error ? error.message : '反馈提交失败，请稍后重试', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-sm text-gray-500 hover:text-primary-600 dark:text-gray-400 dark:hover:text-primary-300 transition-colors"
      >
        反馈
      </button>

      {open && createPortal(
        <div
          className="fixed inset-0 z-[200] overflow-y-auto bg-black/40"
          role="presentation"
          onMouseDown={() => setOpen(false)}
        >
          {/* 挂到 body，避免首页 CardNav 的 transform 把 fixed 弹层裁出视口 */}
          <div className="flex min-h-full items-center justify-center p-4 sm:p-6">
          <form
            className="w-full max-w-md max-h-[calc(100vh-2rem)] overflow-y-auto rounded-xl bg-white dark:bg-gray-900 p-5 shadow-2xl"
            onSubmit={submit}
            onMouseDown={event => event.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="feedback-title"
          >
            <div className="flex items-center justify-between mb-4">
              <h2 id="feedback-title" className="text-lg font-semibold text-gray-900 dark:text-gray-100">
                提交反馈
              </h2>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 text-xl"
                aria-label="关闭反馈窗口"
              >
                ×
              </button>
            </div>

            <label className="block text-sm text-gray-700 dark:text-gray-300 mb-1" htmlFor="feedback-type">
              反馈类型
            </label>
            <select
              id="feedback-type"
              value={feedbackType}
              onChange={event => setFeedbackType(event.target.value)}
              className="w-full mb-4 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
            >
              {FEEDBACK_TYPES.map(item => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>

            <label className="block text-sm text-gray-700 dark:text-gray-300 mb-1" htmlFor="feedback-content">
              反馈内容
            </label>
            <textarea
              id="feedback-content"
              value={content}
              onChange={event => setContent(event.target.value)}
              maxLength={5000}
              rows={5}
              placeholder="告诉我们你的想法或遇到的问题"
              className="w-full resize-none rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
              required
            />

            <label className="block text-sm text-gray-700 dark:text-gray-300 mt-4 mb-1" htmlFor="feedback-contact">
              联系方式（选填）
            </label>
            <input
              id="feedback-contact"
              value={contact}
              onChange={event => setContact(event.target.value)}
              maxLength={255}
              placeholder="邮箱或其他联系方式"
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm"
            />

            <div className="flex justify-end gap-2 mt-5">
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="rounded-lg px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800"
              >
                取消
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-lg bg-primary-600 px-4 py-2 text-sm text-white hover:bg-primary-700 disabled:opacity-50"
              >
                {submitting ? '提交中…' : '提交反馈'}
              </button>
            </div>
          </form>
          </div>
        </div>,
        document.body
      )}
    </>
  )
}
