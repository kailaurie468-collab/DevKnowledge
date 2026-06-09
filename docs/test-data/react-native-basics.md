# React Native 入门基础组件

> 最后更新：2026 年 5 月 | 基于 React Native 0.76 + React 19

## 1. 环境搭建

### 1.1 创建项目

```bash
# 使用 Expo（推荐新手）
npx create-expo-app MyApp --template blank-typescript

# 或使用 React Native CLI
npx @react-native-community/cli init MyApp
```

### 1.2 项目结构

```
MyApp/
├── src/
│   ├── components/     # 可复用组件
│   ├── screens/        # 页面组件
│   ├── navigation/     # 导航配置
│   ├── hooks/          # 自定义 Hooks
│   ├── services/       # API 服务
│   ├── store/          # 状态管理
│   ├── utils/          # 工具函数
│   └── types/          # TypeScript 类型
├── App.tsx             # 入口文件
└── package.json
```

## 2. 核心组件

### 2.1 View - 视图容器

View 是构建 UI 的最基本组件，相当于 Web 中的 `div`。

```tsx
import { View, StyleSheet } from 'react-native'

export function Box() {
  return (
    <View style={styles.container}>
      <View style={styles.card}>
        {/* 子内容 */}
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  card: {
    width: 200,
    height: 100,
    backgroundColor: 'white',
    borderRadius: 8,
    padding: 16,
    // 阴影（iOS）
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    // 阴影（Android）
    elevation: 5,
  },
})
```

### 2.2 Text - 文本显示

```tsx
import { Text, StyleSheet } from 'react-native'

export function Typography() {
  return (
    <>
      <Text style={styles.heading}>标题文本</Text>
      <Text style={styles.body}>
        这是一段普通文本，支持
        <Text style={styles.bold}>加粗</Text>
        和
        <Text style={styles.italic}>斜体</Text>
      </Text>
      <Text style={styles.caption} numberOfLines={2}>
        这是一段很长的说明文本，超过两行会被截断显示省略号...
      </Text>
    </>
  )
}

const styles = StyleSheet.create({
  heading: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1a1a1a',
    marginBottom: 8,
  },
  body: {
    fontSize: 16,
    lineHeight: 24,
    color: '#333',
  },
  bold: {
    fontWeight: 'bold',
  },
  italic: {
    fontStyle: 'italic',
  },
  caption: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
  },
})
```

### 2.3 Image - 图片显示

```tsx
import { Image, StyleSheet } from 'react-native'

export function Avatar({ uri }: { uri: string }) {
  return (
    <Image
      source={{ uri }}
      style={styles.avatar}
      resizeMode="cover"
    />
  )
}

// 本地图片
export function Logo() {
  return (
    <Image
      source={require('../assets/logo.png')}
      style={styles.logo}
    />
  )
}

const styles = StyleSheet.create({
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
  },
  logo: {
    width: 120,
    height: 40,
  },
})
```

### 2.4 TextInput - 文本输入

```tsx
import { useState } from 'react'
import { View, TextInput, Text, StyleSheet } from 'react-native'

export function LoginInput() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  return (
    <View style={styles.container}>
      <Text style={styles.label}>邮箱</Text>
      <TextInput
        style={styles.input}
        value={email}
        onChangeText={setEmail}
        placeholder="请输入邮箱"
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
      />

      <Text style={styles.label}>密码</Text>
      <TextInput
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        placeholder="请输入密码"
        secureTextEntry
        autoComplete="password"
      />
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    backgroundColor: 'white',
  },
})
```

### 2.5 ScrollView - 滚动视图

```tsx
import { ScrollView, View, Text, StyleSheet } from 'react-native'

export function ScrollList({ items }: { items: string[] }) {
  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
    >
      {items.map((item, index) => (
        <View key={index} style={styles.item}>
          <Text>{item}</Text>
        </View>
      ))}
    </ScrollView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    padding: 16,
    gap: 8,
  },
  item: {
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 8,
  },
})
```

### 2.6 FlatList - 高性能列表

```tsx
import { FlatList, View, Text, StyleSheet } from 'react-native'

interface Item {
  id: string
  title: string
}

export function ItemList({ items }: { items: Item[] }) {
  return (
    <FlatList
      data={items}
      keyExtractor={(item) => item.id}
      renderItem={({ item }) => (
        <View style={styles.item}>
          <Text style={styles.title}>{item.title}</Text>
        </View>
      )}
      ItemSeparatorComponent={() => <View style={styles.separator} />}
      ListEmptyComponent={
        <Text style={styles.empty}>暂无数据</Text>
      }
      contentContainerStyle={styles.list}
    />
  )
}

const styles = StyleSheet.create({
  list: {
    padding: 16,
  },
  item: {
    padding: 16,
    backgroundColor: 'white',
    borderRadius: 8,
  },
  title: {
    fontSize: 16,
  },
  separator: {
    height: 8,
  },
  empty: {
    textAlign: 'center',
    color: '#999',
    marginTop: 32,
  },
})
```

## 3. 按钮组件

### 3.1 TouchableOpacity - 透明度反馈

```tsx
import { TouchableOpacity, Text, StyleSheet } from 'react-native'

interface ButtonProps {
  title: string
  onPress: () => void
  variant?: 'primary' | 'secondary' | 'outline'
  disabled?: boolean
}

export function Button({ title, onPress, variant = 'primary', disabled }: ButtonProps) {
  return (
    <TouchableOpacity
      style={[
        styles.button,
        styles[variant],
        disabled && styles.disabled,
      ]}
      onPress={onPress}
      disabled={disabled}
      activeOpacity={0.7}
    >
      <Text style={[styles.text, variant === 'outline' && styles.outlineText]}>
        {title}
      </Text>
    </TouchableOpacity>
  )
}

const styles = StyleSheet.create({
  button: {
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
  },
  primary: {
    backgroundColor: '#007AFF',
  },
  secondary: {
    backgroundColor: '#f0f0f0',
  },
  outline: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  disabled: {
    opacity: 0.5,
  },
  text: {
    color: 'white',
    fontSize: 16,
    fontWeight: '600',
  },
  outlineText: {
    color: '#007AFF',
  },
})
```

### 3.2 Pressable - 更灵活的按压组件

```tsx
import { Pressable, Text, StyleSheet } from 'react-native'

export function IconButton({ icon, label, onPress }: {
  icon: string
  label: string
  onPress: () => void
}) {
  return (
    <Pressable
      style={({ pressed }) => [
        styles.button,
        pressed && styles.pressed,
      ]}
      onPress={onPress}
    >
      <Text style={styles.icon}>{icon}</Text>
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderRadius: 8,
    backgroundColor: '#f5f5f5',
  },
  pressed: {
    opacity: 0.7,
    backgroundColor: '#e0e0e0',
  },
  icon: {
    fontSize: 20,
  },
  label: {
    fontSize: 14,
    color: '#333',
  },
})
```

## 4. 样式与布局

### 4.1 Flexbox 布局

```tsx
import { View, StyleSheet } from 'react-native'

export function FlexLayout() {
  return (
    // 水平排列
    <View style={styles.row}>
      <View style={styles.box1} />
      <View style={styles.box2} />
      <View style={styles.box3} />
    </View>
  )
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    gap: 8,
  },
  box1: { width: 50, height: 50, backgroundColor: 'red' },
  box2: { width: 50, height: 50, backgroundColor: 'green' },
  box3: { width: 50, height: 50, backgroundColor: 'blue' },
})
```

### 4.2 响应式布局

```tsx
import { View, useWindowDimensions, StyleSheet } from 'react-native'

export function ResponsiveLayout() {
  const { width } = useWindowDimensions()
  const isTablet = width >= 768

  return (
    <View style={[styles.container, isTablet && styles.tabletContainer]}>
      {/* 内容根据屏幕宽度自适应 */}
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  tabletContainer: {
    paddingHorizontal: 48,
    maxWidth: 960,
    alignSelf: 'center',
  },
})
```

## 5. 导航（React Navigation）

### 5.1 安装与配置

```bash
npm install @react-navigation/native @react-navigation/native-stack
npm install react-native-screens react-native-safe-area-context
```

### 5.2 基本导航

```tsx
import { NavigationContainer } from '@react-navigation/native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'

type RootStackParamList = {
  Home: undefined
  Profile: { userId: string }
  Settings: undefined
}

const Stack = createNativeStackNavigator<RootStackParamList>()

export function AppNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen
          name="Profile"
          component={ProfileScreen}
          options={{ title: '个人资料' }}
        />
        <Stack.Screen name="Settings" component={SettingsScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  )
}

// 使用导航
import { useNavigation } from '@react-navigation/native'
import type { NativeStackNavigationProp } from '@react-navigation/native-stack'

export function HomeScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>()

  return (
    <Button
      title="查看资料"
      onPress={() => navigation.navigate('Profile', { userId: '123' })}
    />
  )
}
```

### 5.3 底部标签导航

```bash
npm install @react-navigation/bottom-tabs
```

```tsx
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs'

const Tab = createBottomTabNavigator()

export function MainTabs() {
  return (
    <Tab.Navigator>
      <Tab.Screen
        name="Home"
        component={HomeScreen}
        options={{
          tabBarIcon: ({ color }) => <Text style={{ color }}> </Text>,
        }}
      />
      <Tab.Screen
        name="Search"
        component={SearchScreen}
        options={{
          tabBarIcon: ({ color }) => <Text style={{ color }}> </Text>,
        }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileScreen}
        options={{
          tabBarIcon: ({ color }) => <Text style={{ color }}> </Text>,
        }}
      />
    </Tab.Navigator>
  )
}
```

## 6. 状态管理

### 6.1 useState + useContext

```tsx
import { createContext, useContext, useState } from 'react'

interface AuthState {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null)

  const login = async (email: string, password: string) => {
    const user = await api.login(email, password)
    setUser(user)
  }

  const logout = () => setUser(null)

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
```

### 6.2 Zustand（推荐）

```bash
npm install zustand
```

```tsx
import { create } from 'zustand'

interface TodoStore {
  todos: Todo[]
  addTodo: (text: string) => void
  toggleTodo: (id: string) => void
  removeTodo: (id: string) => void
}

export const useTodoStore = create<TodoStore>((set) => ({
  todos: [],
  addTodo: (text) =>
    set((state) => ({
      todos: [...state.todos, { id: Date.now().toString(), text, completed: false }],
    })),
  toggleTodo: (id) =>
    set((state) => ({
      todos: state.todos.map((t) =>
        t.id === id ? { ...t, completed: !t.completed } : t
      ),
    })),
  removeTodo: (id) =>
    set((state) => ({
      todos: state.todos.filter((t) => t.id !== id),
    })),
}))

// 使用
export function TodoList() {
  const { todos, addTodo, toggleTodo } = useTodoStore()
  // ...
}
```

## 7. 网络请求

### 7.1 使用 fetch

```tsx
const API_BASE = 'https://api.example.com'

async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  return response.json()
}

// 使用示例
interface User {
  id: string
  name: string
  email: string
}

export const userApi = {
  getUsers: () => fetchApi<User[]>('/users'),
  getUser: (id: string) => fetchApi<User>(`/users/${id}`),
  createUser: (data: CreateUserRequest) =>
    fetchApi<User>('/users', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
}
```

### 7.2 自定义 Hook

```tsx
import { useState, useEffect } from 'react'

function useApi<T>(fetcher: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    fetcher()
      .then(setData)
      .catch(setError)
      .finally(() => setLoading(false))
  }, [])

  return { data, loading, error }
}

// 使用
function UserList() {
  const { data: users, loading, error } = useApi(userApi.getUsers)

  if (loading) return <ActivityIndicator />
  if (error) return <Text>加载失败</Text>

  return (
    <FlatList
      data={users}
      renderItem={({ item }) => <UserCard user={item} />}
    />
  )
}
```

## 8. 常用第三方库

| 库名 | 用途 | 安装命令 |
|------|------|---------|
| react-navigation | 导航 | `npm install @react-navigation/native` |
| zustand | 状态管理 | `npm install zustand` |
| react-query | 数据请求 | `npm install @tanstack/react-query` |
| mmkv | 本地存储 | `npm install react-native-mmkv` |
| reanimated | 动画 | `npm install react-native-reanimated` |
| gesture-handler | 手势 | `npm install react-native-gesture-handler` |
| flash-list | 高性能列表 | `npm install @shopify/flash-list` |
| nativewind | 样式 | `npm install nativewind tailwindcss` |

## 9. 调试技巧

### 9.1 React Native Debugger

```bash
# iOS
npx react-native run-ios

# Android
npx react-native run-android

# 打开调试菜单
# iOS: Cmd + D
# Android: Cmd + M (macOS) 或 Ctrl + M (Windows)
```

### 9.2 Flipper

```bash
# 安装 Flipper
brew install --cask flipper
```

### 9.3 Console 日志

```tsx
// 基本日志
console.log('Debug:', data)

// 格式化输出
console.log(JSON.stringify(data, null, 2))

// 性能测量
console.time('operation')
// ... 操作
console.timeEnd('operation')
```

## 10. 总结

| 组件 | 用途 | 关键属性 |
|------|------|---------|
| View | 容器 | style, children |
| Text | 文本 | numberOfLines, style |
| Image | 图片 | source, resizeMode |
| TextInput | 输入 | value, onChangeText, placeholder |
| ScrollView | 滚动 | contentContainerStyle |
| FlatList | 列表 | data, renderItem, keyExtractor |
| TouchableOpacity | 按钮 | onPress, activeOpacity |
| Pressable | 按压 | onPress, style 函数 |
