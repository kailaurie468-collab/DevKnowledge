-- ============================================================
-- 种子数据：框架 + 知识链接
-- URL 使用文档实际页面地址，部分包含锚点用于深链接跳转
-- ============================================================

-- 框架
INSERT INTO frameworks (id, name, slug, base_url, icon_url, description, category) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'React',          'react',          'https://react.dev',                   NULL, '用于构建用户界面的 JavaScript 库',             'frontend'),
    ('a0000000-0000-0000-0000-000000000002', 'Vue.js',         'vue',            'https://vuejs.org',                   NULL, '渐进式 JavaScript 框架',                     'frontend'),
    ('a0000000-0000-0000-0000-000000000003', 'Spring Boot',    'spring-boot',    'https://docs.spring.io/spring-boot',  NULL, 'Java 企业级应用开发框架',                     'backend'),
    ('a0000000-0000-0000-0000-000000000004', 'Android',        'android',        'https://developer.android.com',       NULL, 'Google 移动操作系统开发平台',                   'mobile'),
    ('a0000000-0000-0000-0000-000000000005', 'Next.js',        'nextjs',         'https://nextjs.org/docs',             NULL, 'React 全栈框架，支持 SSR/SSG',                'frontend');

-- React 知识链接（react.dev 每个 Hook 有独立页面，无需锚点）
INSERT INTO knowledge_links (id, framework_id, title, url, anchor, description, tags, popularity_score) VALUES
    ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001',
     'useState', 'https://react.dev/reference/react/useState', NULL,
     '在函数组件中添加状态', ARRAY['hooks', 'state', 'react'], 95),
    ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001',
     'useEffect', 'https://react.dev/reference/react/useEffect', NULL,
     '在函数组件中执行副作用', ARRAY['hooks', 'effect', 'lifecycle', 'side-effect'], 98),
    ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001',
     'useRef', 'https://react.dev/reference/react/useRef', NULL,
     '引用 DOM 元素或保存不触发重渲染的值', ARRAY['hooks', 'ref', 'dom'], 80),
    ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000001',
     'useContext', 'https://react.dev/reference/react/useContext', NULL,
     '在组件树中传递数据而无需逐层传递 props', ARRAY['hooks', 'context', 'state-management'], 85),
    ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000001',
     'useMemo', 'https://react.dev/reference/react/useMemo', NULL,
     '缓存计算结果，避免每次渲染重复计算', ARRAY['hooks', 'memo', 'performance'], 75),
    ('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000001',
     'useCallback', 'https://react.dev/reference/react/useCallback', NULL,
     '缓存函数定义，避免子组件不必要的重渲染', ARRAY['hooks', 'callback', 'performance'], 72),
    ('b0000000-0000-0000-0000-000000000007', 'a0000000-0000-0000-0000-000000000001',
     '自定义 Hook', 'https://react.dev/learn/reusing-logic-with-custom-hooks', NULL,
     '将组件逻辑提取为可复用的函数', ARRAY['hooks', 'custom', 'reusable'], 88),
    ('b0000000-0000-0000-0000-000000000008', 'a0000000-0000-0000-0000-000000000001',
     '表单管理', 'https://react.dev/reference/react-dom/components/form', NULL,
     '使用 form 元素构建交互式表单', ARRAY['form', 'state', 'validation', 'input'], 82),
    ('b0000000-0000-0000-0000-000000000009', 'a0000000-0000-0000-0000-000000000001',
     'Context 传递数据', 'https://react.dev/learn/passing-data-deeply-with-context', NULL,
     '跨组件共享状态的官方方案', ARRAY['context', 'state-management', 'global-state'], 86),
    ('b0000000-0000-0000-0000-000000000010', 'a0000000-0000-0000-0000-000000000001',
     'memo 性能优化', 'https://react.dev/reference/react/memo', NULL,
     '跳过不必要的组件重渲染', ARRAY['performance', 'memo', 'optimization'], 90),
    ('b0000000-0000-0000-0000-000000000030', 'a0000000-0000-0000-0000-000000000001',
     'useReducer', 'https://react.dev/reference/react/useReducer', NULL,
     '使用 reducer 管理复杂状态逻辑', ARRAY['hooks', 'reducer', 'state', 'dispatch'], 78),
    ('b0000000-0000-0000-0000-000000000031', 'a0000000-0000-0000-0000-000000000001',
     'useLayoutEffect', 'https://react.dev/reference/react/useLayoutEffect', NULL,
     '在浏览器绘制前同步执行副作用', ARRAY['hooks', 'effect', 'layout', 'dom'], 65),
    ('b0000000-0000-0000-0000-000000000032', 'a0000000-0000-0000-0000-000000000001',
     'useImperativeHandle', 'https://react.dev/reference/react/useImperativeHandle', NULL,
     '自定义暴露给父组件的 ref 方法', ARRAY['hooks', 'ref', 'imperative'], 55),
    ('b0000000-0000-0000-0000-000000000033', 'a0000000-0000-0000-0000-000000000001',
     'useDeferredValue', 'https://react.dev/reference/react/useDeferredValue', NULL,
     '延迟更新 UI 的非关键部分', ARRAY['hooks', 'performance', 'concurrent'], 60),
    ('b0000000-0000-0000-0000-000000000034', 'a0000000-0000-0000-0000-000000000001',
     'useTransition', 'https://react.dev/reference/react/useTransition', NULL,
     '将状态更新标记为非阻塞过渡', ARRAY['hooks', 'transition', 'concurrent'], 62),
    ('b0000000-0000-0000-0000-000000000035', 'a0000000-0000-0000-0000-000000000001',
     'useId', 'https://react.dev/reference/react/useId', NULL,
     '生成唯一 ID 用于无障碍属性', ARRAY['hooks', 'id', 'accessibility'], 50);

-- Vue.js 知识链接
INSERT INTO knowledge_links (id, framework_id, title, url, anchor, description, tags, popularity_score) VALUES
    ('b0000000-0000-0000-0000-000000000011', 'a0000000-0000-0000-0000-000000000002',
     '组合式 API', 'https://vuejs.org/guide/extras/composition-api-faq.html', NULL,
     '使用 setup() 函数组织组件逻辑', ARRAY['composition-api', 'setup', 'hooks'], 90),
    ('b0000000-0000-0000-0000-000000000012', 'a0000000-0000-0000-0000-000000000002',
     '响应式基础', 'https://vuejs.org/guide/essentials/reactivity-fundamentals.html', NULL,
     'ref() 和 reactive() 的使用', ARRAY['reactivity', 'ref', 'reactive'], 92),
    ('b0000000-0000-0000-0000-000000000013', 'a0000000-0000-0000-0000-000000000002',
     '侦听器', 'https://vuejs.org/guide/essentials/watchers.html', NULL,
     'watch() 和 watchEffect() 响应数据变化', ARRAY['watch', 'watcher', 'effect'], 80),
    ('b0000000-0000-0000-0000-000000000014', 'a0000000-0000-0000-0000-000000000002',
     'Vue Router', 'https://router.vuejs.org/', NULL,
     'Vue.js 官方路由管理器', ARRAY['router', 'navigation', 'spa'], 88),
    ('b0000000-0000-0000-0000-000000000015', 'a0000000-0000-0000-0000-000000000002',
     'Pinia 状态管理', 'https://pinia.vuejs.org/', NULL,
     'Vue 官方推荐的状态管理库', ARRAY['state-management', 'pinia', 'store'], 85),
    ('b0000000-0000-0000-0000-000000000036', 'a0000000-0000-0000-0000-000000000002',
     'computed 计算属性', 'https://vuejs.org/guide/essentials/computed.html', NULL,
     '基于响应式依赖缓存计算结果', ARRAY['computed', 'reactivity', 'cache'], 82),
    ('b0000000-0000-0000-0000-000000000037', 'a0000000-0000-0000-0000-000000000002',
     '组件 Props', 'https://vuejs.org/guide/components/props.html', NULL,
     '父组件向子组件传递数据', ARRAY['props', 'component', 'data-flow'], 80);

-- Spring Boot 知识链接（docs.spring.io 使用锚点跳转到具体章节）
INSERT INTO knowledge_links (id, framework_id, title, url, anchor, description, tags, popularity_score) VALUES
    ('b0000000-0000-0000-0000-000000000016', 'a0000000-0000-0000-0000-000000000003',
     '快速入门', 'https://docs.spring.io/spring-boot/docs/current/reference/html/getting-started.html', 'getting-started',
     'Spring Boot 项目创建和基础配置', ARRAY['quickstart', 'getting-started', 'config'], 95),
    ('b0000000-0000-0000-0000-000000000017', 'a0000000-0000-0000-0000-000000000003',
     'Web 开发', 'https://docs.spring.io/spring-boot/docs/current/reference/html/web.html', 'web.servlet.spring-web',
     'RESTful API、Servlet 容器配置', ARRAY['web', 'rest', 'api', 'servlet'], 90),
    ('b0000000-0000-0000-0000-000000000018', 'a0000000-0000-0000-0000-000000000003',
     '数据访问', 'https://docs.spring.io/spring-boot/docs/current/reference/html/data.html', 'data.sql',
     'JPA、JDBC、Flyway 数据库集成', ARRAY['data', 'jpa', 'jdbc', 'flyway', 'database'], 88),
    ('b0000000-0000-0000-0000-000000000019', 'a0000000-0000-0000-0000-000000000003',
     'Spring Security', 'https://docs.spring.io/spring-boot/docs/current/reference/html/web.html', 'web.security',
     '认证授权、JWT、OAuth2 集成', ARRAY['security', 'jwt', 'oauth', 'authentication'], 92),
    ('b0000000-0000-0000-0000-000000000020', 'a0000000-0000-0000-0000-000000000003',
     '配置文件', 'https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html', NULL,
     'application.yml 配置详解', ARRAY['config', 'properties', 'yaml', 'profile'], 85);

-- Android 知识链接
INSERT INTO knowledge_links (id, framework_id, title, url, anchor, description, tags, popularity_score) VALUES
    ('b0000000-0000-0000-0000-000000000021', 'a0000000-0000-0000-0000-000000000004',
     'Jetpack Compose', 'https://developer.android.com/jetpack/compose', NULL,
     '声明式 UI 框架', ARRAY['compose', 'ui', 'declarative'], 95),
    ('b0000000-0000-0000-0000-000000000022', 'a0000000-0000-0000-0000-000000000004',
     'Activity 生命周期', 'https://developer.android.com/guide/components/activities/activity-lifecycle', NULL,
     'Activity 各阶段回调详解', ARRAY['activity', 'lifecycle', 'state'], 88),
    ('b0000000-0000-0000-0000-000000000023', 'a0000000-0000-0000-0000-000000000004',
     'Room 数据库', 'https://developer.android.com/training/data-storage/room', NULL,
     'SQLite 抽象层，编译时验证 SQL', ARRAY['database', 'room', 'sqlite', 'persistence'], 82),
    ('b0000000-0000-0000-0000-000000000024', 'a0000000-0000-0000-0000-000000000004',
     'MVVM 架构', 'https://developer.android.com/topic/architecture', NULL,
     'Model-View-ViewModel 架构模式', ARRAY['architecture', 'mvvm', 'viewmodel', 'livedata'], 90);

-- Next.js 知识链接
INSERT INTO knowledge_links (id, framework_id, title, url, anchor, description, tags, popularity_score) VALUES
    ('b0000000-0000-0000-0000-000000000025', 'a0000000-0000-0000-0000-000000000005',
     'App Router', 'https://nextjs.org/docs/app', NULL,
     '基于文件系统的路由，支持 RSC', ARRAY['router', 'app', 'rsc'], 95),
    ('b0000000-0000-0000-0000-000000000026', 'a0000000-0000-0000-0000-000000000005',
     '数据获取', 'https://nextjs.org/docs/app/building-your-application/data-fetching', NULL,
     'Server Components 中的数据获取策略', ARRAY['data-fetching', 'server', 'cache'], 88),
    ('b0000000-0000-0000-0000-000000000027', 'a0000000-0000-0000-0000-000000000005',
     'API Routes', 'https://nextjs.org/docs/app/building-your-application/routing/route-handlers', NULL,
     '在 Next.js 中创建 API 端点', ARRAY['api', 'route', 'handler', 'backend'], 85),
    ('b0000000-0000-0000-0000-000000000028', 'a0000000-0000-0000-0000-000000000005',
     '中间件', 'https://nextjs.org/docs/app/building-your-application/routing/middleware', NULL,
     '请求拦截和重定向', ARRAY['middleware', 'redirect', 'auth'], 78);
