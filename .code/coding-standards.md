# 编码规范（coding-standards）

以本仓库既有代码为准提炼，不是通用最佳实践。每条尽量给到真实文件出处。参照样本统一取 content-service 那一套（分层最完整、注释密度和仓库平均值一致）。

## 1. Maven 模块与包布局

```
pom.xml                     # groupId org.tiglor / artifactId vidora-cloud / version 1.0.0-SNAPSHOT
vidora-common/              # 聚合 POM：common-core / common-log / common-redis / common-user / common-test
vidora-api/                 # 聚合 POM：vidora-api-{服务名}，服务间调用契约（由被调用方拥有）
vidora-integration/         # 第三方适配层（防腐层）：平台外部系统的客户端与模型转换
vidora-gateway/             # gateway-service (8080)，WebFlux
vidora-auth/                # auth-service   (8101)
vidora-modules/             # 聚合 POM，7 个业务服务
  vidora-video/     (8102)  vidora-content/ (8103)  vidora-interact/ (8104)
  vidora-message/   (8105)  vidora-search/  (8106)  vidora-recommend/ (8107)
  vidora-system/    (8108)
```

Java 包根是 `org.tiglor.{service}`（`vidora-common/*` 下是 `org.tiglor.common.{core,log,redis,user,test}`，`vidora-api-*` 下是 `org.tiglor.api.{服务名}`、契约 DTO 固定放 `.dto` 子包，`vidora-integration` 下是 `org.tiglor.integration.{provider}`）。**这是既有名字，不要改成 `com.vidora` 或 `org.vidora`**；改包名会同时打断 `@SpringBootApplication(scanBasePackages = "org.tiglor")`（见每个服务的启动类）、各 `MybatisPlusConfig` 的 `@MapperScan("org.tiglor.xxx.mapper")` 和 Nacos 上的服务身份。

调用契约的三条硬规则（背景与 Dubbo 3 的触发条件见 `docs/ARCHITECTURE.md` 6.2.1）：

1. **契约由被调用方拥有**。A 要调 B，interface + DTO 加进 `vidora-api/vidora-api-B`，**不许在 A 的模块里另抄一份**（`RemoteUserDTO` 原先就抄在 `vidora-video/client/dto`，字段与 `User` 全靠人肉对齐）。新增一个 `vidora-api-{服务名}` 要同时登记 `vidora-api/pom.xml` 的 `<modules>` 和根 `pom.xml` 的 `dependencyManagement`。
2. **契约模块不引 `spring-web`**，所以里面不能出现 `@GetMapping` / `@RequestBody` 这类 HTTP 注解，接口方法只描述能力——这样同一份契约将来才能被 Dubbo provider `implements`。
3. **HTTP 注解落在调用方**：调用方的 Feign 接口 `extends` 契约并重新声明方法（带 `@Override`），签名或返回类型一改就编译失败。**但字段级漂移锁不住**（provider 的出口仍是自己的 `User` 实体，不是 `RemoteUserDTO`），所以注释里**不要**写成「编译期保证字段一致」，只说保证签名。

每个业务服务的目录结构固定为：

```
src/main/java/org/tiglor/{service}/
  {Service}Application.java     @SpringBootApplication(scanBasePackages="org.tiglor") + @EnableDiscoveryClient
  config/                       MybatisPlusConfig（分页拦截器 + @MapperScan + AutoFillHandler Bean）、SecurityConfig
  controller/                   HTTP 出口，返回 ApiResult
  service/                      接口，extends IService<Entity>
  service/impl/                 实现，extends ServiceImpl<Mapper, Entity> implements XxxService
  mapper/                       interface XxxMapper extends BaseMapper<Entity>，标 @Mapper
  entity/                       表映射
  dto/                          入参对象 + 出参投影对象（见第 4 节）
  vo/                           仅在 system 存在：MenuVO / OperLogVO
  enums/                        仅 content 有：AuditTargetType / FeedType / ManualResult / RiskLevel
  sink/                         仅 system 有：DatabaseLogSink
  mq/ client/                   仅 video 有：RocketMQ 装配与跨服务 Feign 客户端
src/main/resources/
  application.yml               单文件，全部配置都在这里（未接 Nacos 配置中心）
  logback-spring.xml            只有一句 <include resource="logback/logback-base.xml"/>
```

新加东西先想清楚落在哪一层。跨服务共用的实体（RBAC 那几张 `sys_*`）放 `vidora-common/common-user`，不要在某服务里复制一份 —— 历史上 auth 与 system 各有一份 User，已经合并掉（根 pom 的注释就写着「auth 与 system 共用 user_service 库」）。

## 2. 命名

| 元素 | 规则 | 真实例子 |
| --- | --- | --- |
| 服务目录 / artifactId | `vidora-{域}` | `vidora-content` |
| `spring.application.name` / 网关 `uri` | `{域}-service`，与目录名不同 | `content-service`、`lb://content-service` |
| Controller | 资源名单数 + `Controller`，类上 `@RequestMapping("/{复数}")` | `CategoryController` → `/categories` |
| Service / Impl | `XxxService` / `XxxServiceImpl` | `CategoryService(Impl)` |
| Mapper | `XxxMapper` | `CategoryMapper` |
| Entity | 领域名词，**不带后缀** | `Category`、`Tag`、`HotSearch`、`SecurityAudit` |
| 入参对象 | 主流是 `XxxRequest`；auth 模块用 `XxxDTO` | `CategoryRequest`、`MultipartInitRequest` / `UserLoginDTO`、`UpdateThemeDTO` |
| 出参投影 | `XxxNode` / `XxxVO` / `XxxView` / `XxxCount` 等语义后缀 | `CategoryNode`、`OperLogVO`、`CommentView`、`ActionTypeCount` |
| 权限码 | `{域}:{资源}:{动作}` 两段或三段 | `content:category:manage`、`menu:delete`、`operlog:list` |
| 常量类 | `final class` + 私有构造 | `CacheNames`、`SecurityHeaders`、`TraceIds`、`Category.ROOT_PARENT_ID` |

方法动词开头且语义贴 HTTP：`listEnabled` / `page` / `create` / `update` / `setStatus` / `delete` / `findByTarget` / `reportMachine` / `review`。批量入口用 `ByIds` 语义（`VideoController.batch`）。**不要在同一层里混用 `save`/`add`/`insert` 三种叫法**——MP 的 `save` 只在 impl 内部覆盖时使用。

## 3. Controller 层

模板（照 `vidora-modules/vidora-content/src/main/java/org/tiglor/content/controller/CategoryController.java`）：

```java
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;      // 一律构造注入，字段 final

    @GetMapping("/list")
    public ApiResult<List<Category>> listEnabled() {
        return ApiResult.ok(service.listEnabled());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.INSERT)
    public ApiResult<Category> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResult.ok(service.create(request));
    }
}
```

规矩：

- **URL 不带 `/api` 前缀**。`/api` 是网关的事，靠路由的 `Path=/api/categories/**` + `StripPrefix=1` 剥掉。Controller 上写 `/api/categories` 会变成 `/api/api/categories`。
- 只做四件事：绑定参数、标注权限、转调 service、包装 `ApiResult`。**不写业务分支，不碰 Mapper，不加 `@Transactional`。**
- 注入自己的 service 用 `@RequiredArgsConstructor` + final 字段；需要原始请求信息时才显式声明 `HttpServletRequest` 形参（`AuthController.login` 就是这么把 request 传给 `LoginAuditor` 取 IP/UA 的）。
- 取当前用户走 `UserContext.getUserId()`（`common-core/.../security/UserContext.java`，ThreadLocal，由 `HeaderAuthenticationFilter` 填充），不要把 userId 放进请求体让调用方自证身份。
- 分页参数命名固定 `current` / `size`，都给 `@RequestParam(defaultValue = ...)`：列表页常用 `size` 默认 20，日志查询用 10（`OperLogController`）。上限在 service 层夹（`CategoryServiceImpl.MAX_PAGE_SIZE = 100`，通过 `clampSize`）。
- 校验入参用 `@Valid @RequestBody`，配合 jakarta validation 注解（见第 5 节的 DTO 例子）。multipart 用 `@RequestPart("file") MultipartFile` 并在 `@PostMapping(consumes = "multipart/form-data")` 上写明（`VideoController.upload`）。
- 时间范围查询参数用 `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime`（`OperLogController.page`）。

## 4. Service / Mapper / Entity / DTO / VO 各自放什么

| 层 | 放什么 | 不放什么 | 出处 |
| --- | --- | --- | --- |
| `service/`（接口） | `extends IService<Entity>`；只声明本域业务方法与缓存语义 | 不暴露 Mapper 细节 | `content/service/CategoryService.java` |
| `service/impl/` | 业务规则、完整性校验、事务、缓存注解、幂等处理 | 不读 `HttpServletRequest`、不拼 HTTP 状态码 | `CategoryServiceImpl` |
| `mapper/` | `interface XxxMapper extends BaseMapper<Entity>` + `@Mapper`；**例外：`common-log` 的 `OperLogMapper` / `LoginLogMapper` 刻意不标也不自我装配**，由 system-service 的 `config/MybatisPlusConfig.java` 用多包扫描接管 —— `@MapperScan({"org.tiglor.common.user.mapper", "org.tiglor.common.log.mapper"})`（该文件注释解释了原因）。别的服务不要照抄这个例外 | —— | `common-user/mapper/MenuMapper.java`、`system/config/MybatisPlusConfig.java` |
| `entity/` | 与表列一一对应 | 不放展示字段、不放校验注解 | `content/entity/Category.java` |
| `dto/`（入参） | 外部可写的字段 + 校验注解 | **绝不放 `id` / `createTime` / `isDeleted`** | `content/dto/CategoryRequest.java` |
| `dto/`（投影） | 树节点、聚合计数这类「不是表但要有形状」的输出 | 不加持久化注解 | `CategoryNode`、`ActionTypeCount` |
| `vo/` | 需要给 entity 补展示字段时，**继承 entity 再加字段** | 不重抄一遍字段列表 | `system/vo/OperLogVO extends OperLogEntity` |

三条硬规则及其理由（都是文件里已有的注释）：

1. **写入一律收 Request 对象，不收裸实体**。`CategoryController` 顶部注释：裸实体能让调用方自己填 id、createTime，一次「新增」就变成对任意行的覆写。
2. **VO 继承 entity 而不是复制字段**。`OperLogVO` 注释：这两份必须逐字段对齐，抄一份就是留一处漂移。昵称这类「表里没有、要现补」的字段加在子类上，并注明来自同库批量查询。
3. **自定义 SQL 写在 Mapper 上用注解 + 文本块**，不建 XML。全仓 **0 个 `mapper/*.xml`**（虽然 yml 里配了 `mapper-locations: classpath*:/mapper/*.xml`），复杂查询见 `interact/mapper/InteractActionMapper.countByTarget` 的 `@Select("""...""")`，注释里要写清走哪个索引、为什么值得省一次往返。

Entity 两种写法，按表决定，不要混：

- 表有 `create_time/update_time/is_deleted` → `extends BaseEntity`（`common-core/.../BaseEntity.java`：`@TableId(type=IdType.AUTO)` + `@TableField(fill=...)` + `@TableLogic`），类上 `@Data @EqualsAndHashCode(callSuper = true) @TableName("xxx")`。例：`common-user/entity/User.java`。
- 表没有 `is_deleted`（如 `content_category` 靠 `status` 下架）→ 自己声明 `@TableId` + 两个 fill 字段，**不继承 BaseEntity**，并在类注释里写明为什么不继承。例：`content/entity/Category.java`。

敏感列永远 `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)`（`User.passwordHash`）——只允许写入、不随响应输出，防 BCrypt 哈希外泄。

## 5. 响应包装

唯一外壳：`org.tiglor.common.core.ApiResult<T>`，字段 `{ code, message, data }`；成功恒 `code=200`、`message="success"`。

- 有返回值：`return ApiResult.ok(x);`
- 无返回值：`return ApiResult.ok();`（data 为 null）+ 方法返回 `ApiResult<Void>`。管理端的状态切换、删除都用这个形式。
- 失败**不在 Controller 手动 `ApiResult.error(...)`**，一律抛异常交给 `GlobalExceptionHandler`。`error(...)` 只在两处使用：兜底异常处理，以及各服务 `SecurityConfig` 里手写 401/403 JSON。

分页直接用 MyBatis-Plus 的 `Page<T>` 作为 `data`（`com.baomidou.mybatisplus.extension.plugins.pagination.Page`），前端拿到 `{ records, total, size, current, pages }`。

注意：`common-core/.../PageResult.java` 存在但**全仓零引用**（除自身定义处无任何 import）。新代码沿用 `Page<T>`，**不要引入 `PageResult`，也不要为了「统一」去把现有 16 个 `ApiResult<Page<...>>` 改掉**。

「资源不存在」的正确姿势：`throw new BizException(ResultCode.NOT_FOUND, "分类不存在：" + id)`（HTTP 404）。**不要返回 200 + data:null** —— 这是本轮已被修掉的口径（`CategoryServiceImpl.requireExists`）。少数语义确实是「可能没有」的查询保留 null 并**必须在 Javadoc 写明**，例如 `SecurityAuditController.findByTarget`（「没审过时 data 为 null」）、`VideoController.transcodeTask`。区别在于：前者是「这个对象不该存在」，后者是「关系本来可以不存在」。

## 6. 异常规则

| 场景 | 怎么写 | 结果 |
| --- | --- | --- |
| 业务失败（校验不过、状态非法、重复名） | `throw new BizException(ResultCode.VALIDATE_FAILED, "同一父分类下已经有叫「x」的分类")` | HTTP 400 + code 400 |
| 目标不存在 | `new BizException(ResultCode.NOT_FOUND, msg)` | HTTP 404 |
| 未登录 / token 失效 | `ResultCode.UNAUTHORIZED` | 401 |
| 权限不足 | 交给 Spring Security；`AccessDeniedException` 已由 handler 映射成 403 | 403 |
| 频控 | `ResultCode.TOO_MANY_REQUESTS` | 429 |
| 未知 | 什么都不写，落到 `@ExceptionHandler(Exception.class)` | 500 + 通用「服务异常」，**不外泄内部异常信息** |

要点：

- `ResultCode` 的枚举值刻意与 HTTP 状态码同名（`GlobalExceptionHandler.resolveStatus` 直接 `HttpStatus.resolve(code)`），所以**不要新增一个 9xxx 这种解析不出来的码**；真加了会被归一成 500。
- 消息写给操作者看，中文，带上下文（把冲突的具体值拼进去）。`BizException(String message)` 会退化成 code 500，只有确实无法归类时才用。
- 不要在 Service 里 catch 住异常再返回 `ApiResult.error` —— 那会让 HTTP 状态变回 200，监控里全是假成功（`GlobalExceptionHandler` 的类注释记录了这次事故）。
- 审计、日志、上报这类旁路逻辑自己包 try/catch 只打 warn，绝不让旁路把业务打成 500（`OperLogAspect.around` 的 finally 就是这个形状）。

## 7. 配置与 yml 约定

- **每个服务只有一个 `application.yml`**，不再拆分 profile 文件（曾拆过 50 个配置文件后合并回来）。格式固定：`server.port` → `spring.application.name` → `datasource` → `data.redis` → `cloud.nacos.discovery` → `mybatis-plus` → `springdoc` → `logging`，然后一条 `---` 分隔的 `on-profile: dev` 文档。
- 一切可变值走 `${ENV_VAR:默认值}`，默认值是本地开发值。例：`${SPRING_DATASOURCE_PASSWORD:123456}`、`${JWT_SECRET:vidora-cloud-dev-only-secret-key-change-me-32bytes-min}`、`${SPRING_DATA_REDIS_HOST:127.0.0.1}`。容器里由 `deploy/docker-compose.yml` 的 `x-app-env` 覆盖。
- **密钥不进代码**：`jwt.secret`、Redis/MySQL 口令、`VIDORA_LOG_PATH` 都由环境变量注入；`deploy/.env` 已 gitignore，只提交 `.env.example`。
- MyBatis-Plus 段是九份一致的样板：`map-underscore-to-camel-case: true`、`logic-delete-field: isDeleted`（0/1）、`mapper-locations: classpath*:/mapper/*.xml`。
- **dev profile 才打开 SQL 日志**，生产不开。理由写在 content/system 的 yml 尾部注释里：生产把语句连参数打进日志，等于把业务数据多复制一份。日志包名要跟对：普通服务是 `org.tiglor.{service}.mapper`，共用 common-user 的是 `org.tiglor.common.user.mapper`，查审计落库是 `org.tiglor.common.log.mapper`。
- 新增自定义配置项：写成 `@ConfigurationProperties` 类（参考 `common-log/.../config/LogProperties.java`，前缀 `vidora.log`，含 `path` / `oper-enabled` / `ingest-enabled` / `ingest-service-id`(默认 `system-service`) / `ingest-path`(默认 `/internal/logs`) / `ingest-token` / 长度上限等），带默认值和注释；不要散 `@Value` 在各处。
- Nacos 只用于注册发现。要加配置得改这里的 yml，**别指望 Nacos 控制台**。

## 8. 日志规范

地基：`vidora-common/common-core/src/main/resources/logback/logback-base.xml`，由各服务 `logback-spring.xml` 一行 `<include>` 引入。**要改日志策略改这一份，不要在各服务复制。**（放在 common-core 而不是 common-log 的原因写在文件头注释：网关排掉了 web starter，不能为一个 XML 依赖 common-log。）

产物固定在 `logs/<spring.application.name>/`：

| 文件 | 内容 | 滚动 |
| --- | --- | --- |
| `app.log` | INFO 及以上，排查主现场 | 天 + 大小（默认 100MB / 30 天 / 5GB 上限），gz |
| `error.log` | 只有 ERROR（LevelFilter） | 保留 90 天 |
| `audit.log` | logger 名 `AUDIT`，`additivity=false`，一行一条 JSON | 保留 365 天 |

写日志的纪律：

- 用 `@Slf4j`，占位符 `{}`，**不要字符串拼接、不要 `System.out.println`**（历史上 9 个服务曾用 MP 的 `StdOutImpl` 打印 SQL，已全部去掉）。
- 格式自动带 `[服务名 traceId=xxx] [线程]`，消息里不要再重复这些。
- traceId：下游 servlet 服务由 `common-log/.../trace/TraceIdFilter.java`（`@Order(HIGHEST_PRECEDENCE)`）放入 MDC，键名与响应头常量都在 `common-core/.../support/TraceIds.java`（`HEADER = "X-Trace-Id"`、`MDC_KEY = "traceId"`，注释明确要求与 logback 的 `%X{traceId}` 对齐）。
- **网关禁止使用 MDC**：`vidora-gateway/.../filter/TraceIdGlobalFilter.java` 的类注释说明了原因 —— WebFlux 下一个 Netty event-loop 线程交替推进多个请求，线程绑定的 MDC 会串号。网关只透传头，链路号落日志交给下游。同理 `TraceIds` 之所以放 common-core，就是因为响应式的网关依赖不了 common-log。连带后果要知道：**网关 pom 里没有 `vidora-common-log`**（只 include 了 common-core 那份 XML），因此没有 `TraceIdFilter`，`logs/gateway-service/app.log` 里的 `traceId=` 恒为空 —— 跨服务排查要从下游日志或响应头取值，不要以为网关日志能按链路号 grep 到。
- 网关侧 filter 顺序是有意的：`TraceIdGlobalFilter` order `-100` 早于 `GatewayAuthFilter` order `-1`，这样认证失败的 401 响应头里也带链路号。新加全局 filter 要想清楚插在哪。
- 错误级别选择：5xx 级用 `log.error` 并带异常对象，4xx/业务失败用 `log.warn`（`GlobalExceptionHandler` 就是这么分的）。旁路失败只 `warn` 一行、可 grep、不刷栈（`RemoteLogSink.post`）。
- **日志文件不许落到仓库里**：`.gitignore` 已覆盖 `**/*.log` 与 `**/logs/`；历史散落日志已移到 `logs/legacy/`。不要用 `> xxx.log 2>&1` 在项目根目录制造新的散文件。

## 9. 注释风格

只在「为什么」不显然处写中文注释；代码在做什么靠命名说清楚，不要复述语法。类级 Javadoc 讲这一层的职责与非显然的设计取舍，字段/方法用一行说明取值语义。

反例（废话）：

```java
// 保存实体
save(entity);
```

正例（讲清非显然的原因，本仓库到处都是这种）：

```java
// 这里没有调 listEnabled()：同一个 bean 内部的自调用不走代理，@Cacheable 不会生效
// id 是兜底排序键：sort_order 相同的分类不加它，翻页时顺序会抖
// ne(status) 让「已经是目标值」的情况影响 0 行，据此可以区分「真的改了」和「本来就是这样」
// MySQL 的唯一索引把 NULL 当作互不相同的值，parent_id 要是可空，这个约束对「顶级分类」就形同虚设
```

三类内容**必须**留注释，删掉它们等于给下一个人挖坑：

1. **契约陷阱**：null 语义、字段来自另一张表/另一个库、tinyint 不是布尔、跨库无法建外键（`Category` 类注释）。
2. **安全与合规取舍**：为什么这个接口不打审计、为什么这个路径 permitAll、为什么这张表不给删除口（`OperLogController`、`SecurityConfig`、`SQL/09` 头部）。
3. **版本迁移坑**：Boot 4 / MP 3.5.17 造成的非常规写法，如 `RemoteLogSink.post` 里「用字符串做请求体，绕开 Boot 4 下 Jackson2 / Jackson3 两套转换器并存的选型问题」。

数字枚举一定要写档位（`business_type` 0-其他…9-状态变更、`risk_level` 0-无…3-高），DDL 列注释和 Java 注释两处都要有。

## 10. Boot 4 / MP 3.5.17 改名陷阱（会当场编译失败）

这三条是本仓库最高频的「常识型错误」，动手前先看一眼：

| 你会习惯性地写 | 本仓库实际要写 | 出处 |
| --- | --- | --- |
| `spring-boot-starter-aop` | **`spring-boot-starter-aspectj`** | `vidora-common/common-log/pom.xml`（注释点明 Boot 4 改名） |
| `import com.baomidou.mybatisplus.extension.service.IService;`<br>`import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;` | **`import com.baomidou.mybatisplus.spring.service.IService;`**<br>**`import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;`** | 全仓 41 处一致，例 `content/service/CategoryService.java:4`、`CategoryServiceImpl.java:4` |
| `import tools.jackson.databind.ObjectMapper;`（Boot 4 容器默认 Jackson 3） | **`import com.fasterxml.jackson.databind.ObjectMapper;`**（Jackson 2） | 全部源码只用 `com.fasterxml`，零处 `tools.jackson`；理由见 `common-log/.../sink/AuditJson.java` 类注释 |

其余已在根 pom 注释里固定的约束，改动前先读那段注释：

- 网关用 `spring-cloud-starter-gateway-server-webflux`（SCG 5.0 拆分，老的 `spring-cloud-starter-gateway` 不存在）。
- 用到分页的服务必须额外引 `mybatis-plus-jsqlparser`（3.5.9+ 把 `PaginationInnerInterceptor` 拆出去了）。
- RocketMQ **不用 starter**，直连原生 `rocketmq-client` 并自管 Producer/Consumer 生命周期（`video/mq/RocketMqConfig.java`）。
- maven-compiler-plugin 必须 `<parameters>true</parameters>`（Spring Framework 7 移除了 `LocalVariableTableParameterNameDiscoverer`，缺它 `@Cacheable("#userId")`、`@PreAuthorize` 的 SpEL 参数名解析不了）。
- Nacos 全家桶五个构件必须同钉 3.2.4，回落 3.1.1 会 `NoSuchMethodError`。

## 11. 缓存约定

- 缓存名集中在 `vidora-common/common-redis/.../CacheNames.java`，**新增缓存必须先在这里加常量**，因为缓存名同时决定 Redis key 前缀，跨服务共用一个 Redis 实例要靠它避免键冲突。
- 用法：`@Cacheable(cacheNames = CacheNames.CATEGORY_LIST, key = "'enabled'")`，写入侧 `@CacheEvict(cacheNames = ..., allEntries = true)`。字典类整张列表进单个 key，所以任何增删改都只能整体失效。
- **同类内部自调用不走代理**，`@Cacheable` 会静默失效 —— 需要缓存就在每个公开入口方法上各自标（`CategoryServiceImpl.tree()` 宁可重写一遍查询也不调 `listEnabled()`，注释解释了这件事）。
- 覆盖 `save` / `updateById` / `removeById` 来加 `@CacheEvict`，防止别人绕过你写的业务方法直接改库（`CategoryServiceImpl` 尾部三个 override）。
- 外部可决定 key 空间的视图不要缓存（`CacheNames.SEARCH_SUGGEST_TOP` 注释：只缓存无输入时的热门词，带前缀的联想 key 是用户任意输入，缓存它等于让外部决定 Redis 里有多少条）。
