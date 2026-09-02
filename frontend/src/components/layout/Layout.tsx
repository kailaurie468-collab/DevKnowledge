import { Outlet, Link, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/authStore'
import { ThemeToggle } from '@/components/effects/ThemeToggle'
import CardNav, { CardNavItem } from '@/components/effects/CardNav'
import { FeedbackDialog } from '@/components/FeedbackDialog'
import { Header } from './Header'
import { Sidebar } from './Sidebar'

const navItems: CardNavItem[] = [
  {
    label: "主功能",
    bgColor: "transparent",
    textColor: "#fff",
    links: [
      { label: "首页", href: "/", ariaLabel: "Home" },
      { label: "知识搜索", href: "/knowledge", ariaLabel: "Knowledge Search" },
      { label: "Demo 生成", href: "/demos", ariaLabel: "Demo Generation" },
      { label: "Skills 构建", href: "/skills", ariaLabel: "Skills Building" }
    ]
  },
  {
    label: "知识管理",
    bgColor: "transparent",
    textColor: "#fff",
    links: [
      { label: "知识库", href: "/kb", ariaLabel: "Knowledge Base" },
      { label: "Wiki 知识图谱", href: "/wiki", ariaLabel: "Wiki Graph" }
    ]
  },
  {
    label: "设置",
    bgColor: "transparent",
    textColor: "#fff",
    links: [
      { label: "AI 服务配置", href: "/settings/ai", ariaLabel: "AI Settings" },
      { label: "Embedding AI", href: "/settings/embedding", ariaLabel: "Embedding Settings" },
      { label: "Reranker AI", href: "/settings/reranker", ariaLabel: "Reranker Settings" },
      { label: "数据存储", href: "/settings/storage", ariaLabel: "Storage Settings" },
      { label: "RAG 指标", href: "/settings/rag-metrics", ariaLabel: "RAG Metrics" }
    ]
  }
];

export function Layout() {
  const { user, isAuthenticated, logout } = useAuthStore()
  const location = useLocation()
  const isHome = location.pathname === '/'

  const Logo = (
    <Link to="/" className="text-lg font-bold text-primary-600 dark:text-primary-400">
      DevKnowledge
    </Link>
  );

  const TopRightButtons = (
    <div className="flex items-center gap-4 h-full px-2">
      <ThemeToggle />
      <FeedbackDialog />
      {isAuthenticated ? (
        <>
          <span className="text-sm text-gray-600 dark:text-gray-300 hidden sm:inline">{user?.displayName || user?.email}</span>
          <button
            onClick={logout}
            className="text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-200"
          >
            退出
          </button>
        </>
      ) : (
        <Link
          to="/login"
          className="text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300 font-medium"
        >
          登录
        </Link>
      )}
    </div>
  );

  return (
    <div className={`h-screen flex flex-col transition-colors relative z-0 ${isHome ? '' : 'bg-white dark:bg-gray-950'}`}>
      {isHome ? (
        <CardNav 
          items={navItems}
          logo={Logo}
          button={TopRightButtons}
          baseColor="rgba(0, 0, 0, 0.15)"
          menuColor="#ffffff"
          className="backdrop-blur-md"
        />
      ) : (
        <Header />
      )}
      
      <div className="flex flex-1 overflow-hidden">
        {!isHome && <Sidebar />}
        <main className={`flex-1 flex flex-col transition-colors relative z-10 ${isHome ? 'overflow-hidden pt-28 p-6' : 'overflow-auto p-6'}`}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
