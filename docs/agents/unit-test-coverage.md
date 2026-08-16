> 来源：外部 AI 方法论输出（2026-08-16，无法联网，主代理核实无事实错误，与项目架构一致）。
要保证 Android 项目里“绝大多数业务逻辑”都能被单元测试覆盖，并且 UI 也能有自动化测试，核心不是靠后期补测试，而是靠 **可测试的架构设计 + 分层测试策略 + 覆盖率门禁 + CI 自动化**。

下面给你一套比较完整的落地方案。

---

# 一、总体思路：不要把逻辑写在 Activity / Fragment 里

Android 项目单元测试难做，通常不是因为 Android 本身，而是因为代码结构不可测：

- Activity / Fragment 里直接请求网络；
- ViewModel 里直接 new Retrofit；
- 业务判断散落在 Adapter、Dialog、View 中；
- 时间、随机数、ID、线程调度不可控；
- 数据库、网络、文件、系统服务强耦合。

要让单元测试覆盖绝大多数业务逻辑，第一步是把逻辑从 UI 层剥离出来。

推荐分层：

```text
UI 层
  Activity / Fragment / Compose Screen
  只负责展示和事件转发

表现层
  ViewModel / StateHolder
  负责 UI State、Loading、Error、事件流转

领域层
  UseCase / Domain Model / Validator / Mapper
  纯 Kotlin 业务逻辑，最容易测试

数据层
  Repository
  负责协调本地数据库、网络、缓存

数据源层
  RemoteDataSource / LocalDataSource
  Retrofit、Room、DataStore、File 等
```

这样大部分逻辑都可以用普通 JVM 单元测试覆盖，不需要真机。

---

# 二、业务逻辑单元测试应该覆盖哪些层？

建议按以下顺序建设测试。

---

## 1. Domain / UseCase 层：必须高覆盖

UseCase 应该尽量是纯 Kotlin：

```kotlin
class CalculateOrderPriceUseCase {
    operator fun invoke(items: List<OrderItem>): OrderPrice {
        val totalPrice = items.sumOf { it.price * it.count }
        val discount = if (totalPrice > 100) 10.0 else 0.0
        return OrderPrice(
            total = totalPrice,
            discount = discount,
            payable = totalPrice - discount
        )
    }
}
```

单元测试：

```kotlin
class CalculateOrderPriceUseCaseTest {

    private val useCase = CalculateOrderPriceUseCase()

    @Test
    fun `总价大于100时应有折扣`() {
        val items = listOf(
            OrderItem(price = 60.0, count = 2)
        )

        val result = useCase(items)

        assertEquals(120.0, result.total)
        assertEquals(10.0, result.discount)
        assertEquals(110.0, result.payable)
    }

    @Test
    fun `空列表总价为0`() {
        val result = useCase(emptyList())

        assertEquals(0.0, result.total)
        assertEquals(0.0, result.discount)
        assertEquals(0.0, result.payable)
    }
}
```

这类测试成本最低，价值最高。

应该重点覆盖：

- 正常流程；
- 空数据；
- null 数据；
- 边界值；
- 异常输入；
- 权限不足；
- 金额、数量、时间、日期、时区；
- 排序、过滤、分组、搜索；
- 分页逻辑；
- 状态机逻辑；
- 表单校验；
- 业务规则判断。

---

## 2. Mapper / 数据整理层：必须高覆盖

你提到“信息整理”，这类逻辑非常适合单元测试。

例如 DTO 转 Domain Model：

```kotlin
class UserDtoMapper {
    fun map(dto: UserDto): User {
        return User(
            id = dto.id,
            name = dto.name.trim(),
            phone = dto.phone?.replace(" ", "") ?: "",
            createdAt = dto.createTime?.toInstant() ?: Instant.EPOCH
        )
    }
}
```

测试：

```kotlin
class UserDtoMapperTest {

    private val mapper = UserDtoMapper()

    @Test
    fun `应正确转换用户信息`() {
        val dto = UserDto(
            id = 1,
            name = " 张三 ",
            phone = "138 0000 0000",
            createTime = "2024-01-01T10:00:00Z"
        )

        val user = mapper.map(dto)

        assertEquals(1, user.id)
        assertEquals("张三", user.name)
        assertEquals("13800000000", user.phone)
        assertEquals(Instant.parse("2024-01-01T10:00:00Z"), user.createdAt)
    }

    @Test
    fun `手机号为空时应返回空字符串`() {
        val dto = UserDto(
            id = 1,
            name = "李四",
            phone = null,
            createTime = null
        )

        val user = mapper.map(dto)

        assertEquals("", user.phone)
        assertEquals(Instant.EPOCH, user.createdAt)
    }
}
```

信息整理类逻辑建议重点测：

- JSON 转对象；
- DTO 转 Domain Model；
- Domain Model 转 UI Model；
- 时间格式化；
- 时区转换；
- 金额格式化；
- 手机号、身份证、邮箱脱敏；
- 空字符串、null、异常字段；
- 列表合并、去重、排序、过滤；
- 搜索关键词匹配；
- 分页拼接；
- 错误码转用户可读文案。

---

## 3. Repository 层：覆盖数据编排逻辑

Repository 通常负责：

- 先读缓存还是先读网络；
- 网络失败是否读本地；
- 是否刷新；
- 是否分页；
- 是否保存登录态；
- 是否同步数据库；
- 多数据源合并。

为了让 Repository 可测，不要直接依赖具体 Retrofit 或 Room，而是依赖接口。

```kotlin
interface UserRemoteDataSource {
    suspend fun getUser(id: String): UserDto
}

interface UserLocalDataSource {
    suspend fun getUser(id: String): User?
    suspend fun saveUser(user: User)
}

class UserRepository(
    private val remote: UserRemoteDataSource,
    private val local: UserLocalDataSource
) {
    suspend fun getUser(id: String, forceRefresh: Boolean = false): User {
        if (!forceRefresh) {
            local.getUser(id)?.let { return it }
        }

        val dto = remote.getUser(id)
        val user = UserMapper.map(dto)
        local.saveUser(user)
        return user
    }
}
```

测试时使用 Fake：

```kotlin
class FakeUserRemoteDataSource(
    private var result: Result<UserDto>
) : UserRemoteDataSource {

    var requestCount = 0

    override suspend fun getUser(id: String): UserDto {
        requestCount++
        return result.getOrThrow()
    }
}

class FakeUserLocalDataSource : UserLocalDataSource {

    private val cache = mutableMapOf<String, User>()

    override suspend fun getUser(id: String): User? {
        return cache[id]
    }

    override suspend fun saveUser(user: User) {
        cache[user.id] = user
    }
}
```

测试示例：

```kotlin
class UserRepositoryTest {

    private lateinit var remote: FakeUserRemoteDataSource
    private lateinit var local: FakeUserLocalDataSource
    private lateinit var repository: UserRepository

    @Before
    fun setup() {
        remote = FakeUserRemoteDataSource(
            Result.success(UserDto(id = "1", name = "Tom"))
        )
        local = FakeUserLocalDataSource()
        repository = UserRepository(remote, local)
    }

    @Test
    fun `本地有缓存时不应请求网络`() = runTest {
        local.saveUser(User(id = "1", name = "Tom"))

        repository.getUser("1")

        assertEquals(0, remote.requestCount)
    }

    @Test
    fun `强制刷新时应请求网络`() = runTest {
        local.saveUser(User(id = "1", name = "Tom"))

        repository.getUser("1", forceRefresh = true)

        assertEquals(1, remote.requestCount)
    }

    @Test
    fun `网络成功后应写入本地缓存`() = runTest {
        repository.getUser("1")

        assertEquals(1, remote.requestCount)
        assertNotNull(local.getUser("1"))
    }
}
```

Repository 层应该覆盖：

- 缓存命中；
- 缓存未命中；
- 强制刷新；
- 网络成功；
- 网络失败；
- 本地兜底；
- 分页加载；
- 数据合并；
- 登录态失效；
- Token 刷新；
- 并发请求；
- 数据去重；
- 删除、更新、同步冲突。

---

## 4. ViewModel / UiState 层：覆盖状态流转

ViewModel 不应该做复杂业务计算，它主要负责：

- 调用 UseCase / Repository；
- 管理 Loading、Success、Error；
- 暴露 UI State；
- 处理用户事件。

推荐 ViewModel 输出一个明确状态：

```kotlin
sealed class UserUiState {
    data object Loading : UserUiState()
    data class Success(val user: User) : UserUiState()
    data class Error(val message: String) : UserUiState()
}
```

ViewModel：

```kotlin
class UserViewModel(
    private val repository: UserRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
    val uiState: StateFlow<UserUiState> = _uiState

    fun loadUser(id: String) {
        viewModelScope.launch(dispatcher) {
            _uiState.value = UserUiState.Loading
            runCatching {
                repository.getUser(id)
            }.fold(
                onSuccess = {
                    _uiState.value = UserUiState.Success(it)
                },
                onFailure = {
                    _uiState.value = UserUiState.Error(it.message ?: "未知错误")
                }
            )
        }
    }
}
```

测试时：

- 使用 `TestDispatcher`；
- 使用 Fake Repository；
- 使用 `Turbine` 测试 Flow；
- 使用 `Dispatchers.setMain()` 替换主线程调度器。

示例：

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class UserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `加载成功应进入Success状态`() = runTest(testDispatcher) {
        val repository = FakeUserRepository(
            result = Result.success(User(id = "1", name = "Tom"))
        )
        val viewModel = UserViewModel(repository, testDispatcher)

        viewModel.uiState.test {
            viewModel.loadUser("1")

            assertEquals(UserUiState.Loading, awaitItem())
            assertEquals(UserUiState.Success(User(id = "1", name = "Tom")), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `加载失败应进入Error状态`() = runTest(testDispatcher) {
        val repository = FakeUserRepository(
            result = Result.failure(RuntimeException("网络错误"))
        )
        val viewModel = UserViewModel(repository, testDispatcher)

        viewModel.uiState.test {
            viewModel.loadUser("1")

            assertEquals(UserUiState.Loading, awaitItem())
            assertEquals(UserUiState.Error("网络错误"), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }
}
```

ViewModel 层要重点覆盖：

- 初始状态；
- Loading；
- Success；
- Error；
- Empty；
- 重试；
- 刷新；
- 分页加载更多；
- 表单提交；
- 登录成功后跳转事件；
- 一次性事件，例如 Toast、Snackbar、Navigation；
- 配置变化后状态是否合理；
- 并发事件是否互相干扰。

---

# 三、网络层如何单元测试？

网络处理是可以被单元测试覆盖的。关键原则是：**不要真请求真实服务器**。

网络层可以分成几类测试。

---

## 1. 测试 API 请求和响应解析

可以使用：

```kotlin
implementation "com.squareup.retrofit2:retrofit"
implementation "com.squareup.retrofit2:converter-kotlinx-serialization"
implementation "com.squareup.okhttp3:okhttp"

testImplementation "com.squareup.okhttp3:mockwebserver"
```

示例：

```kotlin
class UserApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: UserApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(KotlinxSerializationConverterFactory.create())
            .build()

        api = retrofit.create(UserApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `应正确解析用户接口`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"1","name":"Tom"}""")
        )

        val user = api.getUser("1")

        assertEquals("1", user.id)
        assertEquals("Tom", user.name)

        val request = server.takeRequest()
        assertEquals("/users/1", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun `接口返回404应抛出异常`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows<HttpException> {
            api.getUser("1")
        }
    }
}
```

这里可以覆盖：

- URL 是否正确；
- Query 参数是否正确；
- Header 是否正确；
- Token 是否带上；
- Body 序列化是否正确；
- 响应 JSON 是否能反序列化；
- 字段缺失是否能兼容；
- 400 / 401 / 403 / 404 / 500 如何处理；
- 超时如何处理；
- 空响应如何处理；
- 分页参数是否正确。

---

## 2. 测试 Interceptor

比如 Token Interceptor：

```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
            .newBuilder()
            .header("Authorization", "Bearer token123")
            .build()

        return chain.proceed(request)
    }
}
```

可以用 MockWebServer 测：

```kotlin
@Test
fun `请求应携带Token`() {
    val server = MockWebServer()
    server.start()
    server.enqueue(MockResponse().setResponseCode(200))

    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())
        .build()

    val request = Request.Builder()
        .url(server.url("/test"))
        .build()

    client.newCall(request).execute()

    val recordedRequest = server.takeRequest()
    assertEquals("Bearer token123", recordedRequest.getHeader("Authorization"))

    server.shutdown()
}
```

如果是 Token 刷新逻辑，也可以模拟多次响应：

```kotlin
@Test
fun `Token过期后应刷新Token并重试请求`() {
    val server = MockWebServer()
    server.start()

    server.enqueue(MockResponse().setResponseCode(401))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"newToken"}"""))
    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":"1"}"""))

    // 构造带 Authenticator / TokenAuthInterceptor 的 OkHttpClient
    // 发起请求并断言最终成功
}
```

网络层需要重点覆盖：

- Token 自动附加；
- Token 过期刷新；
- 刷新失败后退出登录；
- 请求重试；
- 请求取消；
- 超时；
- 公共参数；
- 多语言 Header；
- 设备信息 Header；
- 日志拦截器；
- 加密签名；
- 文件上传；
- 分页参数；
- 错误码统一映射。

---

## 3. Repository 网络策略测试

例如：

```kotlin
class ArticleRepository(
    private val remote: ArticleRemoteDataSource,
    private val local: ArticleLocalDataSource
) {
    suspend fun getArticles(page: Int): List<Article> {
        return try {
            val remoteArticles = remote.getArticles(page)
            local.saveArticles(remoteArticles)
            remoteArticles
        } catch (e: Exception) {
            if (page == 1) {
                local.getArticles()
            } else {
                throw e
            }
        }
    }
}
```

测试：

- 第一页网络失败时读本地；
- 第二页网络失败时抛异常；
- 网络成功后写入本地；
- 本地已有旧数据时是否覆盖；
- 分页是否追加而不是覆盖；
- 空列表是否清缓存；
- 删除文章后本地和远端是否一致。

---

# 四、数据库、缓存、DataStore 如何测试？

## 1. Room 数据库

Room 建议使用内存数据库测试。

如果是 local unit test，可以结合 Robolectric：

```kotlin
testImplementation "org.robolectric:robolectric"
testImplementation "androidx.room:room-testing"
```

或者使用 instrumented test：

```kotlin
androidTestImplementation "androidx.room:room-testing"
```

示例：

```kotlin
@RunWith(RobolectricTestRunner::class)
class UserDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: UserDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = userDao(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `插入用户后应能查询到`() = runTest {
        dao.insert(UserEntity(id = "1", name = "Tom"))

        val user = dao.getById("1")

        assertEquals("Tom", user?.name)
    }
}
```

重点覆盖：

- 插入；
- 更新；
- 删除；
- 查询；
- 分页查询；
- 条件查询；
- 唯一约束；
- 事务；
- 多表关联；
- 数据库升级；
- 缓存失效策略。

---

## 2. DataStore / SharedPreferences

DataStore 测试重点：

- 写入；
- 读取；
- 默认值；
- 清空；
- 多字段更新；
- 异常数据兼容；
- 迁移逻辑。

建议封装接口：

```kotlin
interface UserPreferenceStorage {
    val userId: Flow<String?>
    suspend fun setUserId(userId: String?)
}
```

测试时可用内存实现：

```kotlin
class FakeUserPreferenceStorage : UserPreferenceStorage {
    private val state = MutableStateFlow<String?>(null)

    override val userId: Flow<String?> = state

    override suspend fun setUserId(userId: String?) {
        state.value = userId
    }
}
```

---

# 五、如何保证覆盖率真的高？

覆盖率高不等于质量高，但没有覆盖率指标又很难推动。建议这样做。

---

## 1. 使用覆盖率工具

Android 常用：

- JaCoCo；
- Kover；
- Android Studio Coverage；
- CI 平台集成 Codecov / Coveralls / SonarQube。

### Gradle 使用 JaCoCo 示例

```gradle
apply plugin: 'jacoco'

jacoco {
    toolVersion = "0.8.11"
}

tasks.register('jacocoTestReport', JacocoReport) {
    dependsOn 'testDebugUnitTest'

    reports {
        xml.required = true
        html.required = true
    }

    def fileFilter = [
            '**/R.class',
            '**/R$*.class',
            '**/BuildConfig.*',
            '**/Manifest*.*',
            '**/*Test*.*',
            '**/databinding/**',
            '**/android/databinding/**',
            '**/*_Impl*.*',
            '**/*Binding*.*',
            '**/*Dao_Impl*.*',
            '**/*Module*.*',
            '**/*DI*.*'
    ]

    def debugTree = fileTree(
            dir: "$buildDir/tmp/kotlin-classes/debug",
            excludes: fileFilter
    )

    def mainSrc = "$projectDir/src/main/java"

    sourceDirectories.setFrom(files([mainSrc]))
    classDirectories.setFrom(files([debugTree]))
    executionData.setFrom(fileTree(dir: buildDir, includes: [
            'jacoco/testDebugUnitTest.exec'
    ]))
}
```

也可以在 CI 中执行：

```bash
./gradlew testDebugUnitTest jacocoTestReport
```

---

## 2. 覆盖率不要只看行覆盖率

建议同时看：

- Line Coverage；
- Branch Coverage；
- Method Coverage；
- Class Coverage；
- Diff Coverage，也就是新增代码覆盖率。

其中 **分支覆盖率更重要**。

例如：

```kotlin
fun canLogin(age: Int, agreed: Boolean): Boolean {
    return age >= 18 && agreed
}
```

只测一个 `age = 20, agreed = true`，行覆盖率可能 100%，但分支覆盖率不一定高。

需要覆盖：

```text
age < 18
age = 18
age > 18
agreed = true
agreed = false
```

---

## 3. 设置覆盖率门禁

可以按模块设置不同目标。

建议参考：

| 模块 | 建议覆盖率 |
|---|---:|
| Domain / UseCase | 90%+ |
| Mapper / Formatter / Validator | 90%+ |
| Repository | 80%+ |
| ViewModel / StateHolder | 75%+ |
| DataSource | 70%+ |
| 工具类 | 85%+ |
| Activity / Fragment / Compose UI | 不主要靠单元测试 |

注意：不要为了覆盖率测试 getter/setter 或自动生成代码，这样意义不大。

---

## 4. 增量覆盖率比总覆盖率更重要

老项目直接要求 80% 不现实。可以要求：

- 新增代码覆盖率必须达到 80%；
- 修改代码必须补测试；
- 每个 PR 必须带测试；
- 核心业务模块优先补齐；
- Bugfix 必须先写失败用例，再修复。

这比一次性补测试更可持续。

---

## 5. 使用测试用例设计方法

不要只测正常流程。

每个业务方法至少考虑：

### 正常输入

```text
用户已登录
列表有数据
接口返回成功
```

### 边界输入

```text
空列表
只有一个元素
分页第一页
分页最后一页
金额为 0
数量为 0
字符串为空
时间跨天
时间跨月
闰年
时区变化
```

### 异常输入

```text
null
空字符串
非法 JSON
超长字符串
特殊字符
非法手机号
非法邮箱
非法日期
接口 4xx
接口 5xx
网络超时
Token 过期
权限不足
```

### 状态变化

```text
Loading
Success
Error
Empty
Refreshing
LoadMore
Offline
NoPermission
```

---

# 六、Android 单元测试常用技术栈

建议测试依赖：

```gradle
testImplementation "junit:junit:4.13.2"
testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0"
testImplementation "app.cash.turbine:turbine:1.1.0"
testImplementation "io.mockk:mockk:1.13.10"
testImplementation "com.squareup.okhttp3:mockwebserver:4.12.0"
testImplementation "org.robolectric:robolectric:4.12.1"
testImplementation "androidx.arch.core:core-testing:2.2.0"
testImplementation "androidx.test:core-ktx:1.5.0"
testImplementation "androidx.test.ext:junit-ktx:1.1.5"

androidTestImplementation "androidx.test.espresso:espresso-core:3.5.1"
androidTestImplementation "androidx.test.uiautomator:uiautomator:2.3.0"
androidTestImplementation "androidx.compose.ui:ui-test-junit4:1.6.6"
debugImplementation "androidx.compose.ui:ui-test-manifest:1.6.6"
```

如果使用 Hilt：

```gradle
testImplementation "com.google.dagger:hilt-android-testing:2.51.1"
kaptTest "com.google.dagger:hilt-android-compiler:2.51.1"

androidTestImplementation "com.google.dagger:hilt-android-testing:2.51.1"
kaptAndroidTest "com.google.dagger:hilt-android-compiler:2.51.1"
```

---

# 七、UI 自动化测试怎么做？

UI 测试不要试图覆盖所有页面和所有控件，成本太高。UI 自动化应该重点覆盖：

- 核心用户路径；
- 高频页面；
- 关键业务闭环；
- 容易出问题的交互；
- 跨页面流程；
- 异常态展示；
- 权限流程；
- Deep Link；
- 支付、登录、注册、搜索、下单、发布等关键流程。

---

# 八、UI 自动化测试分层

推荐 UI 测试也分层。

```text
1. 组件级 UI 测试
   测试单个 Composable / 自定义 View

2. 页面级 UI 测试
   测试一个页面的不同状态

3. 流程级 UI 测试
   测试跨页面用户流程

4. E2E 测试
   接近真实环境，覆盖完整链路

5. 视觉回归测试
   截图对比，防止 UI 样式被改坏

6. 稳定性测试
   Monkey、App Crawler、长时间运行
```

---

# 九、Compose UI 自动化测试

如果项目使用 Jetpack Compose，UI 测试会相对好写。

示例页面：

```kotlin
@Composable
fun LoginScreen(
    state: LoginUiState,
    onLoginClick: (String, String) -> Unit
) {
    Column {
        TextField(
            value = state.username,
            onValueChange = {},
            modifier = Modifier.testTag("username")
        )

        TextField(
            value = state.password,
            onValueChange = {},
            modifier = Modifier.testTag("password")
        )

        Button(
            onClick = { onLoginClick(state.username, state.password) },
            enabled = state.canLogin,
            modifier = Modifier.testTag("loginButton")
        ) {
            Text("登录")
        }

        if (state.isLoading) {
            Text("登录中...")
        }

        if (state.error != null) {
            Text(state.error)
        }
    }
}
```

测试：

```kotlin
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `初始状态登录按钮不可点击`() {
        composeTestRule.setContent {
            LoginScreen(
                state = LoginUiState(),
                onLoginClick = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithTag("loginButton")
            .assertIsNotEnabled()
    }

    @Test
    fun `输入账号密码后登录按钮可点击`() {
        var clicked = false

        composeTestRule.setContent {
            LoginScreen(
                state = LoginUiState(
                    username = "user",
                    password = "123456",
                    canLogin = true
                ),
                onLoginClick = { _, _ -> clicked = true }
            )
        }

        composeTestRule
            .onNodeWithTag("loginButton")
            .assertIsEnabled()
            .performClick()

        assertTrue(clicked)
    }

    @Test
    fun `登录失败应显示错误信息`() {
        composeTestRule.setContent {
            LoginScreen(
                state = LoginUiState(
                    error = "账号或密码错误"
                ),
                onLoginClick = { _, _ -> }
            )
        }

        composeTestRule
            .onNodeWithText("账号或密码错误")
            .assertIsDisplayed()
    }
}
```

Compose UI 测试建议：

- 给关键组件加 `testTag`；
- UI State 尽量由外部传入；
- 事件回调用参数捕获；
- 不要依赖真实网络；
- 使用 fake 数据；
- 页面状态拆分成 `Loading / Success / Error / Empty`；
- 每个状态都测一次。

---

# 十、传统 View 体系用 Espresso

如果是 XML + Fragment / Activity，用 Espresso。

示例：

```kotlin
@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val activityRule = activityScenarioRule<LoginActivity>()

    @Test
    fun `登录失败应显示错误提示`() {
        onView(withId(R.id.username))
            .perform(typeText("user"))

        onView(withId(R.id.password))
            .perform(typeText("wrong"))

        closeSoftKeyboard()

        onView(withId(R.id.loginButton))
            .perform(click())

        onView(withText("账号或密码错误"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `登录成功应跳转到首页`() {
        onView(withId(R.id.username))
            .perform(typeText("user"))

        onView(withId(R.id.password))
            .perform(typeText("123456"))

        closeSoftKeyboard()

        intending(hasComponent(HomeActivity::class.java.name))
            .respondWith(ActivityResult(RESULT_OK, null))

        onView(withId(R.id.loginButton))
            .perform(click())

        intended(hasComponent(HomeActivity::class.java.name))
    }
}
```

Espresso 测试适合覆盖：

- 输入框；
- 按钮点击；
- 页面跳转；
- Snackbar / Toast；
- Dialog；
- RecyclerView 列表；
- SwipeRefresh；
- ViewPager；
- BottomSheet；
- Navigation 跳转；
- 权限弹窗后的 UI 变化。

---

# 十一、跨 App / 系统级 UI 测试用 UI Automator

有些场景 Espresso 不够用，例如：

- 系统权限弹窗；
- 通知栏；
- 系统设置；
- 跨 App 分享；
- 系统键盘；
- 来电、短信、锁屏；
- 通知点击；
- 后台切回前台。

这时用 UI Automator。

示例：

```kotlin
@Test
fun `允许通知权限`() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    val allowButton = device.findObject(By.text("允许"))
    if (allowButton != null) {
        allowButton.click()
    }
}
```

常见组合：

```text
Espresso / Compose Test：测 App 内 UI
UI Automator：测系统弹窗、通知、跨 App
```

---

# 十二、UI 测试如何避免不稳定？

UI 自动化最大的问题不是写不出来，而是不稳定。

建议：

---

## 1. 关闭动画

测试时关闭系统动画，或者在 App 中提供测试模式关闭动画。

---

## 2. 使用 Idling Resource

异步操作必须等待完成。

Espresso 中：

```kotlin
Espresso.registerIdlingResources(myIdlingResource)
```

Compose 中尽量用状态断言，而不是 `Thread.sleep()`。

不要这样：

```kotlin
Thread.sleep(3000)
```

应该等待明确状态：

```kotlin
composeTestRule.waitUntil(timeoutMillis = 5000) {
    // 某个状态已经出现
}
```

---

## 3. 使用测试专用后端或 Mock Server

UI 测试不要依赖生产环境。

推荐：

- 本地 Mock Server；
- WireMock；
- MockWebServer；
- Staging 环境；
- 测试账号；
- 测试数据自动清理；
- 固定时间；
- 固定用户；
- 固定列表数据。

---

## 4. 使用测试 Build Variant

可以定义：

```text
debug：开发
staging：联调
uiTest：UI 自动化
release：发布
```

`uiTest` 环境可以：

- 关闭动画；
- 使用固定测试账号；
- 使用 Mock API；
- 关闭 Crash 上报；
- 关闭 A/B 实验；
- 关闭推送；
- 使用固定配置；
- 跳过引导页；
- 自动登录测试账号。

---

## 5. 每个测试独立

不要让测试依赖上一个测试的结果。

错误方式：

```text
test1 登录
test2 假设已经登录
```

正确方式：

每个测试自己准备状态，或者通过测试规则快速进入状态。

---

# 十三、UI 自动化应该覆盖哪些场景？

建议优先覆盖以下流程。

---

## 1. 启动与引导

- 首次启动；
- 非首次启动；
- 引导页跳过；
- 自动登录；
- Token 过期后重新登录；
- 隐私协议同意 / 拒绝。

---

## 2. 登录注册

- 空账号；
- 空密码；
- 非法手机号；
- 验证码倒计时；
- 登录成功；
- 登录失败；
- 网络错误；
- 账号冻结；
- 第三方登录；
- 退出登录。

---

## 3. 列表页

- Loading；
- Success；
- Empty；
- Error；
- 下拉刷新；
- 上拉加载更多；
- 分页失败；
- 点击列表项；
- 搜索；
- 筛选；
- 排序。

---

## 4. 详情页

- 数据展示；
- 加载中；
- 加载失败；
- 重试；
- 分享；
- 收藏；
- 点赞；
- 编辑；
- 删除；
- 权限不足。

---

## 5. 表单页

- 必填校验；
- 字数限制；
- 非法输入；
- 提交成功；
- 提交失败；
- 重复提交；
- 网络中断；
- 表单草稿恢复。

---

## 6. 支付 / 订单类

- 创建订单；
- 选择优惠券；
- 金额变化；
- 库存不足；
- 支付取消；
- 支付成功；
- 支付失败；
- 订单状态刷新；
- 超时关闭。

---

## 7. 权限类

- 相机权限允许；
- 相机权限拒绝；
- 永久拒绝后跳设置；
- 定位权限；
- 通知权限；
- 存储权限。

---

# 十四、视觉回归测试

单元测试和 Espresso 主要保证行为正确，但不保证 UI 样式没变。

视觉回归测试用于发现：

- 颜色变了；
- 间距变了；
- 字体变了；
- 图标变了；
- 暗色模式异常；
- 多语言截断；
- 大字体模式异常；
- RTL 布局异常；
- 不同尺寸屏幕异常。

可用工具：

- Paparazzi；
- Showkase；
- Roborazzi；
- Compose Preview Screenshot Testing；
- Firebase Test Lab 截图；
- Maestro 截图；
- Appium 截图对比。

Compose 示例思路：

```kotlin
@Test
fun loginScreenLoadingSnapshot() {
    paparazzi.snapshot {
        LoginScreen(
            state = LoginUiState(isLoading = true),
            onLoginClick = {}
        )
    }
}
```

建议：

- 只覆盖关键组件；
- 每个主题测一次；
- 暗色模式测一次；
- 大字体测一次；
- 多语言测一次；
- 不要所有页面全量截图，否则维护成本高。

---

# 十五、E2E 测试如何组织？

E2E 测试不要太多，应该少而关键。

推荐策略：

```text
单元测试：大量
Repository / UseCase / ViewModel 测试：大量
Robolectric / 组件 UI 测试：中等
Espresso / Compose 流程测试：中等
E2E：少量但关键
视觉测试：少量关键组件
Monkey / 稳定性：定期执行
```

E2E 覆盖：

- 登录；
- 搜索；
- 详情；
- 下单；
- 支付；
- 发布；
- 消息；
- 个人中心；
- 退出登录；
- 崩溃恢复；
- 网络异常恢复。

可以使用：

- Espresso；
- Compose UI Test；
- UI Automator；
- Maestro；
- Appium；
- Firebase Test Lab；
- AWS Device Farm；
- 阿里云真机测试平台。

如果是中小团队，推荐优先：

```text
Compose UI Test / Espresso
+ MockWebServer / Staging
+ 少量关键 E2E
```

如果团队已经有较成熟自动化平台，再考虑 Appium 或设备农场。

---

# 十六、Robolectric 适合什么场景？

Robolectric 可以在 JVM 上运行 Android 测试，速度比真机快。

适合：

- 测试 Activity 生命周期；
- 测试 Fragment；
- 测试 ViewModel 与 Android 组件交互；
- 测试 Resources；
- 测试 SharedPreferences；
- 测试 Room；
- 测试 WorkManager；
- 测试广播；
- 测试部分 View 逻辑。

Gradle：

```gradle
testImplementation "org.robolectric:robolectric:4.12.1"

android {
    testOptions {
        unitTests {
            includeAndroidResources = true
        }
    }
}
```

但是 Robolectric 不能完全替代真机，尤其是：

- 真实渲染；
- 动画；
- 手势；
- 性能；
- 真实输入法；
- 系统权限弹窗；
- 厂商 ROM 差异；
- 相机；
- 蓝牙；
- 推送；
- 后台限制。

所以：

```text
逻辑测试：优先普通 JVM 单元测试
Android 组件相关：Robolectric
真实交互：Espresso / Compose UI Test
系统级交互：UI Automator
```

---

# 十七、WorkManager、后台任务如何测试？

WorkManager 是 Android 上常见后台逻辑，也应该测试。

依赖：

```gradle
testImplementation "androidx.work:work-testing:2.9.0"
```

示例：

```kotlin
@Test
fun `同步任务应上传本地数据`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val config = Configuration.Builder()
        .setExecutor(SynchronousExecutor())
        .build()

    WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

    val request = OneTimeWorkRequestBuilder<SyncWorker>().build()

    val workManager = WorkManager.getInstance(context)
    val testDriver = WorkManagerTestInitHelper.getTestDriver(context)

    workManager.enqueue(request).result.get()

    testDriver?.setAllConstraintsMet(request.id)

    val info = workManager.getWorkInfoById(request.id).get()

    assertEquals(WorkInfo.State.SUCCEEDED, info.state)
}
```

需要覆盖：

- 任务成功；
- 任务失败；
- 重试；
- 约束条件；
- 网络可用；
- 充电状态；
- 低电量；
- 重复任务；
- 取消任务；
- 数据输入输出；
- 与 Repository 的交互。

---

# 十八、依赖注入是测试的基础

如果代码里到处是：

```kotlin
val api = RetrofitClient.api
val db = AppDatabase.getInstance(context)
```

测试会很难写。

建议使用 Hilt、Koin 或手写 DI。

例如 Hilt：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }
}
```

测试时替换：

```kotlin
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkModule::class]
)
object TestNetworkModule {

    @Provides
    @Singleton
    fun provideUserApi(): UserApi {
        return FakeUserApi()
    }
}
```

这样 Repository、ViewModel、UseCase 都更容易注入 Fake。

---

# 十九、时间、随机数、ID 必须可注入

很多业务逻辑测试失败，是因为依赖不可控。

不要这样：

```kotlin
fun isToday(): Boolean {
    return LocalDate.now() == targetDate
}
```

应该这样：

```kotlin
class DateChecker(private val clock: Clock) {
    fun isToday(date: LocalDate): Boolean {
        return date == LocalDate.now(clock)
    }
}
```

测试：

```kotlin
@Test
fun `日期是今天应返回true`() {
    val clock = Clock.fixed(
        Instant.parse("2024-01-01T10:00:00Z"),
        ZoneOffset.UTC
    )

    val checker = DateChecker(clock)

    assertTrue(checker.isToday(LocalDate.of(2024, 1, 1)))
}
```

同理，随机数、UUID、订单号生成器也要注入：

```kotlin
interface IdGenerator {
    fun nextId(): String
}
```

测试中使用固定实现：

```kotlin
class FakeIdGenerator : IdGenerator {
    override fun nextId(): String = "fixed-id"
}
```

---

# 二十、线程调度必须可注入

不要在业务代码里写死：

```kotlin
Dispatchers.IO
Dispatchers.Main
GlobalScope
Thread.sleep
```

推荐通过构造函数注入：

```kotlin
class SyncUseCase(
    private val repository: OrderRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

测试时：

```kotlin
val testDispatcher = StandardTestDispatcher()
val useCase = SyncUseCase(repository, testDispatcher)
```

ViewModel 测试中：

```kotlin
@Before
fun setup() {
    Dispatchers.setMain(StandardTestDispatcher())
}

@After
fun tearDown() {
    Dispatchers.resetMain()
}
```

这样可以避免测试 flaky。

---

# 二十一、推荐的测试目录结构

```text
app/src/main/java/com/example/app
├── ui
├── viewmodel
├── domain
├── data
├── network
├── storage
└── util

app/src/test/java/com/example/app
├── domain
│   └── CalculateOrderPriceUseCaseTest.kt
├── data
│   └── UserRepositoryTest.kt
├── viewmodel
│   └── UserViewModelTest.kt
├── network
│   ├── UserApiTest.kt
│   └── AuthInterceptorTest.kt
├── mapper
│   └── UserDtoMapperTest.kt
└── util
    └── DateFormatterTest.kt

app/src/androidTest/java/com/example/app
├── ui
│   ├── LoginScreenTest.kt
│   ├── HomeFlowTest.kt
│   └── OrderFlowTest.kt
├── database
│   └── UserDaoInstrumentedTest.kt
└── e2e
    └── PurchaseE2ETest.kt
```

---

# 二十二、CI 中如何跑？

建议 CI 分阶段。

## PR 阶段

必须快：

```bash
./gradlew lint
./gradlew testDebugUnitTest
./gradlew jacocoTestReport
```

要求：

- 单元测试必须通过；
- 新增代码覆盖率达标；
- 静态检查通过；
- 不允许降低核心模块覆盖率。

---

## Merge 后

跑更多测试：

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

可以跑：

- 关键 UI 测试；
- Robolectric；
- WorkManager 测试；
- Room 测试；
- Compose / Espresso smoke test。

---

## Nightly

跑完整测试：

- 全量 UI 测试；
- E2E；
- 多设备；
- 多版本 Android；
- 截图对比；
- Monkey；
- 性能基线；
- 启动时间；
- 内存泄漏检查。

---

# 二十三、覆盖率落地建议

如果项目现在几乎没有测试，不要一上来要求 90%。

推荐路线：

## 第一阶段：建立基础

目标：

- 新代码必须有测试；
- 核心工具类、Mapper、Validator 覆盖率 80%+；
- CI 跑单元测试；
- 生成覆盖率报告。

---

## 第二阶段：覆盖核心业务

目标：

- 登录、注册、订单、支付、搜索等核心 UseCase 覆盖率 85%+；
- ViewModel 状态流转覆盖率 70%+；
- Repository 缓存策略覆盖率 75%+；
- 网络层 Mock 测试覆盖主要接口。

---

## 第三阶段：建设 UI 自动化

目标：

- 核心流程 UI 自动化；
- 登录、下单、搜索、详情、发布等主路径；
- 关键异常态；
- 权限流程；
- Deep Link；
- 暗色模式 / 多语言基础验证。

---

## 第四阶段：稳定性与质量门禁

目标：

- Flaky test 治理；
- E2E 稳定性；
- 测试报告；
- 覆盖率门禁；
- 变异测试；
- 性能测试；
- 崩溃率监控；
- ANR 监控。

---

# 二十四、不要只追求覆盖率数字

覆盖率高不等于测试有效。

低质量测试示例：

```kotlin
@Test
fun test() {
    val result = calculator.add(1, 2)
    assertNotNull(result)
}
```

这种测试覆盖率高，但断言弱。

高质量测试应该断言业务结果：

```kotlin
assertEquals(3, calculator.add(1, 2))
```

测试应该验证：

- 输出是否正确；
- 状态是否正确；
- 调用次数是否正确；
- 异常是否抛出；
- 错误信息是否友好；
- 边界条件是否符合业务；
- 副作用是否正确，例如数据库写入、缓存更新、事件发送。

---

# 二十五、最终推荐策略总结

你可以按这个原则执行：

```text
1. 业务逻辑尽量放 UseCase / Mapper / Repository / ViewModel，不要放 Activity / Fragment。
2. 网络、数据库、时间、线程、ID、随机数全部通过依赖注入。
3. 网络层用 MockWebServer / Fake DataSource 测试。
4. 信息整理逻辑用纯函数测试，覆盖空值、边界值、异常值。
5. ViewModel 用 TestDispatcher + Fake Repository + Turbine 测试状态流转。
6. Room / DataStore 用内存数据库或 Fake 实现测试。
7. 使用 JaCoCo / Kover 生成覆盖率报告，并设置新增代码覆盖率门禁。
8. UI 自动化优先覆盖核心流程，不追求所有页面全覆盖。
9. Compose 用 Compose UI Test，传统 View 用 Espresso，系统弹窗用 UI Automator。
10. E2E 少而精，使用测试环境或 Mock Server，避免依赖生产环境。
11. 视觉回归用截图测试补充。
12. CI 上自动运行单元测试、覆盖率检查和关键 UI 测试。
```

---

# 二十六、一个比较合理的测试比例

建议：

```text
单元测试：70%
集成 / Repository / API 测试：20%
UI / E2E 测试：10%
```

但按业务价值看，应该是：

```text
核心业务逻辑：必须高覆盖
网络解析与错误处理：必须覆盖
数据缓存与同步：必须覆盖
UI 主流程：必须自动化
UI 边缘场景：选择性覆盖
视觉样式：关键页面截图测试
```

最终目标不是“所有代码 100% 覆盖”，而是：

> 核心业务改代码时，测试能快速告诉你哪里坏了；  
> UI 自动化能保证关键用户路径不会回归；  
> 覆盖率门禁能保证新代码不继续烂下去。