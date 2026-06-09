# Android Jetpack ViewModel 开发规范

> 最后更新：2026 年 5 月 | 基于 Jetpack Lifecycle 2.8.x + Java 17

## 1. ViewModel 基础概念

### 1.1 什么是 ViewModel

ViewModel 是 Android Jetpack 架构组件之一，用于**以注重生命周期的方式存储和管理界面相关数据**。ViewModel 类让数据可在发生屏幕旋转等配置更改后继续存在。

### 1.2 核心职责

- 为界面提供数据
- 在配置更改后保存数据
- 协调 ViewModel 与其他组件的工作（如 Repository）
- 处理业务逻辑

### 1.3 生命周期

ViewModel 的生命周期比 Activity/Fragment 更长：

```
Activity Created → ViewModel Created → Activity Started → Activity Resumed
→ Activity Paused → Activity Stopped → Activity Destroyed（配置更改）
→ 新 Activity Created → 复用同一个 ViewModel
```

## 2. ViewModel 最佳实践

### 2.1 基本创建方式

```java
public class UserViewModel extends ViewModel {

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final UserRepository repository;

    public UserViewModel(UserRepository repository) {
        this.repository = repository;
    }

    public LiveData<User> getUser() {
        return user;
    }

    public void loadUser(String userId) {
        repository.getUser(userId, new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                user.setValue(result);
            }

            @Override
            public void onError(Exception e) {
                // 处理错误
            }
        });
    }
}
```

### 2.2 使用 ViewModelProvider 创建

```java
public class UserActivity extends AppCompatActivity {

    private UserViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);

        // 创建 ViewModel
        UserViewModelFactory factory = new UserViewModelFactory(
                new UserRepository()
        );
        viewModel = new ViewModelProvider(this, factory).get(UserViewModel.class);

        // 观察数据
        viewModel.getUser().observe(this, user -> {
            if (user != null) {
                updateUI(user);
            }
        });

        // 加载数据
        viewModel.loadUser("123");
    }

    private void updateUI(User user) {
        // 更新界面
    }
}
```

### 2.3 自定义 ViewModelFactory

```java
public class UserViewModelFactory implements ViewModelProvider.Factory {

    private final UserRepository repository;

    public UserViewModelFactory(UserRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(UserViewModel.class)) {
            return (T) new UserViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
```

### 2.4 使用 Hilt 注入依赖

```java
@HiltViewModel
public class UserViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final AnalyticsTracker analyticsTracker;

    @Inject
    public UserViewModel(UserRepository userRepository, AnalyticsTracker analyticsTracker) {
        this.userRepository = userRepository;
        this.analyticsTracker = analyticsTracker;
    }

    // ...
}
```

```java
// 在 Activity 中使用 Hilt
@AndroidEntryPoint
public class UserActivity extends AppCompatActivity {

    private final UserViewModel viewModel;

    public UserActivity() {
        viewModel = new ViewModelProvider(this).get(UserViewModel.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ...
    }
}
```

## 3. 状态管理规范

### 3.1 使用 LiveData 暴露状态

```java
public class UserViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<User> getUser() {
        return user;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadUser() {
        isLoading.setValue(true);
        error.setValue(null);

        repository.getUser(new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                user.setValue(result);
                isLoading.setValue(false);
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e.getMessage());
                isLoading.setValue(false);
            }
        });
    }
}
```

### 3.2 使用 MediatorLiveData 组合多个数据源

```java
public class DashboardViewModel extends ViewModel {

    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<List<Order>> orders = new MutableLiveData<>();
    private final MediatorLiveData<DashboardState> dashboardState = new MediatorLiveData<>();

    public DashboardViewModel() {
        // 组合多个 LiveData
        dashboardState.addSource(user, u -> updateDashboard());
        dashboardState.addSource(orders, o -> updateDashboard());
    }

    public LiveData<DashboardState> getDashboardState() {
        return dashboardState;
    }

    private void updateDashboard() {
        User u = user.getValue();
        List<Order> o = orders.getValue();

        if (u != null && o != null) {
            dashboardState.setValue(new DashboardState(u, o));
        }
    }

    public void loadDashboard() {
        repository.getUser(new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                user.setValue(result);
            }
            @Override
            public void onError(Exception e) { }
        });

        repository.getOrders(new Callback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> result) {
                orders.setValue(result);
            }
            @Override
            public void onError(Exception e) { }
        });
    }
}
```

### 3.3 避免在 ViewModel 中持有 Context

```java
// ❌ 错误：持有 Activity 引用会导致内存泄漏
public class UserViewModel extends ViewModel {
    private Context context; // 内存泄漏！

    public UserViewModel(Context context) {
        this.context = context;
    }
}

// ✅ 正确：使用 Application Context
@HiltViewModel
public class UserViewModel extends ViewModel {
    private final Context appContext;

    @Inject
    public UserViewModel(@ApplicationContext Context context) {
        this.appContext = context;
    }
}
```

### 3.4 一次性事件处理（SingleLiveEvent）

```java
/**
 * 只触发一次的 LiveData（用于导航、Toast 等一次性事件）
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

    private final AtomicBoolean pending = new AtomicBoolean(false);

    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, t -> {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t);
            }
        });
    }

    @Override
    public void setValue(T value) {
        pending.set(true);
        super.setValue(value);
    }
}

// 使用
public class UserViewModel extends ViewModel {

    private final SingleLiveEvent<Void> navigateBack = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> showToast = new SingleLiveEvent<>();

    public SingleLiveEvent<Void> getNavigateBack() {
        return navigateBack;
    }

    public SingleLiveEvent<String> getShowToast() {
        return showToast;
    }

    public void deleteUser() {
        repository.deleteUser(new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showToast.setValue("删除成功");
                navigateBack.setValue(null);
            }

            @Override
            public void onError(Exception e) {
                showToast.setValue("删除失败: " + e.getMessage());
            }
        });
    }
}
```

## 4. ViewModel 与后台任务

### 4.1 使用 ExecutorService 执行后台任务

```java
public class UserViewModel extends ViewModel {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown(); // ViewModel 销毁时取消任务
    }

    public void loadUser(String userId) {
        isLoading.setValue(true);

        executor.execute(() -> {
            try {
                // 后台执行耗时操作
                User user = repository.getUserSync(userId);

                // 切回主线程更新 UI
                mainHandler.post(() -> {
                    this.user.setValue(user);
                    isLoading.setValue(false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    error.setValue(e.getMessage());
                    isLoading.setValue(false);
                });
            }
        });
    }
}
```

### 4.2 使用 AsyncTask（已废弃，不推荐）

```java
// ❌ AsyncTask 已在 API 30 废弃，不推荐使用
// ✅ 推荐使用 ExecutorService 或 Kotlin 协程
```

### 4.3 并行请求

```java
public class DashboardViewModel extends ViewModel {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger pendingTasks = new AtomicInteger(0);

    public void loadDashboard() {
        isLoading.setValue(true);
        pendingTasks.set(2);

        // 并行请求用户信息
        executor.execute(() -> {
            try {
                User user = repository.getUserSync();
                userLiveData.setValue(user);
            } catch (Exception e) {
                error.setValue(e.getMessage());
            } finally {
                checkComplete();
            }
        });

        // 并行请求订单列表
        executor.execute(() -> {
            try {
                List<Order> orders = repository.getOrdersSync();
                ordersLiveData.setValue(orders);
            } catch (Exception e) {
                error.setValue(e.getMessage());
            } finally {
                checkComplete();
            }
        });
    }

    private void checkComplete() {
        if (pendingTasks.decrementAndGet() == 0) {
            mainHandler.post(() -> isLoading.setValue(false));
        }
    }
}
```

### 4.4 错误处理

```java
public class UserViewModel extends ViewModel {

    public void loadUser() {
        isLoading.setValue(true);

        repository.getUser(new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                user.setValue(result);
                isLoading.setValue(false);
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e.getMessage());
                isLoading.setValue(false);

                // 记录错误日志
                Log.e("UserViewModel", "加载用户失败", e);
            }
        });
    }
}
```

## 5. ViewModel 与其他组件协作

### 5.1 ViewModel + Repository 模式

```java
// Repository 接口
public interface UserRepository {
    void getUser(String userId, Callback<User> callback);
    void saveUser(User user, Callback<Void> callback);
    LiveData<User> observeUser(String userId);
}

// Repository 实现
public class UserRepositoryImpl implements UserRepository {

    private final UserDao userDao;           // 本地数据源
    private final UserApiService apiService; // 远程数据源

    public UserRepositoryImpl(UserDao userDao, UserApiService apiService) {
        this.userDao = userDao;
        this.apiService = apiService;
    }

    @Override
    public void getUser(String userId, Callback<User> callback) {
        // 先从本地获取
        executor.execute(() -> {
            User localUser = userDao.getUserById(userId);
            if (localUser != null) {
                callback.onSuccess(localUser);
            } else {
                // 本地没有，从网络获取
                apiService.getUser(userId).enqueue(new retrofit2.Callback<User>() {
                    @Override
                    public void onResponse(Call<User> call, Response<User> response) {
                        if (response.isSuccessful()) {
                            User user = response.body();
                            // 保存到本地
                            executor.execute(() -> userDao.insert(user));
                            callback.onSuccess(user);
                        } else {
                            callback.onError(new Exception("请求失败"));
                        }
                    }

                    @Override
                    public void onFailure(Call<User> call, Throwable t) {
                        callback.onError((Exception) t);
                    }
                });
            }
        });
    }
}
```

### 5.2 ViewModel + Room 数据库

```java
// Room DAO
@Dao
public interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    LiveData<User> getUserById(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(User user);

    @Delete
    void delete(User user);
}

// ViewModel 使用 Room
public class UserViewModel extends ViewModel {

    private final UserDao userDao;

    public UserViewModel(UserDao userDao) {
        this.userDao = userDao;
    }

    // Room 返回 LiveData，自动更新
    public LiveData<User> getUser(String userId) {
        return userDao.getUserById(userId);
    }

    public void saveUser(User user) {
        executor.execute(() -> userDao.insert(user));
    }
}
```

### 5.3 ViewModel + Retrofit 网络请求

```java
// Retrofit 接口
public interface UserApiService {
    @GET("users/{id}")
    Call<User> getUser(@Path("id") String userId);

    @POST("users")
    Call<User> createUser(@Body User user);
}

// ViewModel 使用 Retrofit
public class UserViewModel extends ViewModel {

    private final UserApiService apiService;

    public UserViewModel(UserApiService apiService) {
        this.apiService = apiService;
    }

    public void loadUser(String userId) {
        isLoading.setValue(true);

        apiService.getUser(userId).enqueue(new retrofit2.Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                if (response.isSuccessful() && response.body() != null) {
                    user.setValue(response.body());
                } else {
                    error.setValue("请求失败: " + response.code());
                }
                isLoading.setValue(false);
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                error.setValue(t.getMessage());
                isLoading.setValue(false);
            }
        });
    }
}
```

## 6. 测试 ViewModel

### 6.1 单元测试

```java
@RunWith(MockitoJUnitRunner.class)
public class UserViewModelTest {

    @Mock
    private UserRepository repository;

    @Mock
    private Observer<User> userObserver;

    @Mock
    private Observer<String> errorObserver;

    @Captor
    private ArgumentCaptor<Callback<User>> callbackCaptor;

    private UserViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        viewModel = new UserViewModel(repository);
    }

    @Test
    public void loadUser_shouldUpdateStateWithUser() {
        // Given
        User expectedUser = new User("1", "Test User");
        viewModel.getUser().observeForever(userObserver);

        // When
        viewModel.loadUser("1");
        verify(repository).getUser(eq("1"), callbackCaptor.capture());
        callbackCaptor.getValue().onSuccess(expectedUser);

        // Then
        verify(userObserver).onChanged(expectedUser);
    }

    @Test
    public void loadUser_shouldHandleError() {
        // Given
        viewModel.getError().observeForever(errorObserver);

        // When
        viewModel.loadUser("1");
        verify(repository).getUser(eq("1"), callbackCaptor.capture());
        callbackCaptor.getValue().onError(new Exception("Network error"));

        // Then
        verify(errorObserver).onChanged("Network error");
    }
}
```

### 6.2 使用 InstantTaskExecutorRule

```java
@RunWith(MockitoJUnitRunner.class)
public class UserViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    // 这样 LiveData.setValue() 会立即执行，不需要后台线程
}
```

## 7. 常见错误与解决方案

### 7.1 在 ViewModel 中启动 Activity

```java
// ❌ 错误
public class UserViewModel extends ViewModel {
    public void openSettings(Context context) {
        context.startActivity(new Intent(context, SettingsActivity.class));
    }
}

// ✅ 正确：发送事件，让 UI 层处理导航
public class UserViewModel extends ViewModel {
    private final SingleLiveEvent<Void> navigateToSettings = new SingleLiveEvent<>();

    public SingleLiveEvent<Void> getNavigateToSettings() {
        return navigateToSettings;
    }

    public void onSettingsClick() {
        navigateToSettings.setValue(null);
    }
}
```

### 7.2 在 Activity 中创建新 ViewModel

```java
// ❌ 错误：每次 onCreate 都会创建新 ViewModel
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    UserViewModel viewModel = new UserViewModel(); // 错误！
}

// ✅ 正确：使用 ViewModelProvider
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    UserViewModel viewModel = new ViewModelProvider(this).get(UserViewModel.class);
}
```

### 7.3 LiveData observe 使用错误的 LifecycleOwner

```java
// ❌ 错误：使用 getApplicationContext() 会导致生命周期问题
viewModel.getUser().observe(getApplicationContext(), user -> {
    // ...
});

// ✅ 正确：使用 Activity/Fragment 作为 LifecycleOwner
viewModel.getUser().observe(this, user -> {
    // ...
});
```

## 8. 总结

| 规范 | 说明 |
|------|------|
| 使用 LiveData | 暴露可观察的数据 |
| ViewModelProvider | 正确创建 ViewModel |
| ViewModelFactory | 自定义依赖注入 |
| SingleLiveEvent | 处理一次性事件 |
| 不持有 Context | 使用 @ApplicationContext |
| Repository 模式 | 分离数据源 |
| ExecutorService | 后台任务执行 |
| onCleared() | 清理资源 |
| 单元测试 | Mock 依赖 + 验证行为 |
