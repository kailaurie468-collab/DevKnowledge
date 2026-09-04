import { useEffect, useState } from 'react'
import {
  adminApi,
  type AdminPageResponse,
  type AdminUser,
} from '@/api/admin'
import { Pager, formatDate, handleAdminError } from './shared'
import { useNotify } from '@/stores/notify'

/** 后台-用户：列表 + 活跃/用量聚合 */
export function AdminUsersSection() {
  const { notify } = useNotify()
  const [users, setUsers] = useState<AdminPageResponse<AdminUser> | null>(null)
  const [loading, setLoading] = useState(true)

  const loadPage = (page: number) => {
    setLoading(true)
    adminApi
      .users(page, users?.size || 20)
      .then(setUsers)
      .catch(error => notify(handleAdminError(error, '用户列表加载失败'), 'error'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadPage(1)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-gray-500">
            <tr>
              <th className="pb-2 pr-4">邮箱</th>
              <th className="pb-2 pr-4">昵称</th>
              <th className="pb-2 pr-4">注册时间</th>
              <th className="pb-2 pr-4">最近活跃</th>
              <th className="pb-2 pr-4">累计 Token</th>
              <th className="pb-2 pr-4">Demo</th>
              <th className="pb-2">反馈</th>
            </tr>
          </thead>
          <tbody className="text-gray-800 dark:text-gray-200">
            {(users?.items ?? []).map(u => (
              <tr key={u.id} className="border-t border-gray-100 dark:border-gray-800">
                <td className="py-2 pr-4">{u.email}</td>
                <td className="py-2 pr-4">{u.displayName || '-'}</td>
                <td className="whitespace-nowrap py-2 pr-4">{formatDate(u.createdAt)}</td>
                <td className="whitespace-nowrap py-2 pr-4">
                  {u.lastActiveAt ? formatDate(u.lastActiveAt) : '从未'}
                </td>
                <td className="py-2 pr-4">{u.totalTokens}</td>
                <td className="py-2 pr-4">{u.demoCount}</td>
                <td className="py-2">{u.feedbackCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {users && users.totalPages > 0 && (
        <Pager pageInfo={users} loading={loading} onPage={loadPage} />
      )}
    </section>
  )
}
