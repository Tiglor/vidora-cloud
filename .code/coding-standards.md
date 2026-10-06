# 编码规范（coding-standards）

以本仓库既有代码为准提炼，不是通用最佳实践。每条尽量给到真实文件出处。参照样本统一取 content-service 那一套（分层最完整、注释密度和仓库平均值一致）。

## 1. Maven 模块与包布局

```
pom.xml                     # groupId org.tiglor / artifactId vidora-cloud / version 1.0.0-SNAPSHOT
vidora-common/              # 聚合 POM：common-core / common-log / common-redis / common-test
vidora-api/                 # 聚合 POM：vidora-api-{服务名}，服务间调用契约（由被调用方拥有）
vidora-integration/         # 第三方适配层（防腐层）：平台外部系统的客户端与模型转换
vidora-gateway/             # gateway-service (8080)，WebFlux
vidora-auth/                # auth-service   (8101)，**不连库**：没有 mybatis-plus starter 也没有 mysql 驱动
vidora-modules/             # 聚合 POM，7 个业务服务
  vidora-video/     (8102)  vidora-content/ (8103)  vidora-interact/ (8104)
  vidora-message/   (8105)  vidora-search/  (8106)  vidora-recommend/ (8107)
  vidora-system/    (8108)  # RBAC 实体与 Mapper 的唯一归属，兼 Dubbo provider (20880)
```

Java 包根是 `org.tiglor.{service}`（`vidora-common/*` 下是 `org.tiglor.common.{core,log,redis,test}`，`vidora-api-*` 下是 `org.tiglor.api.{服务名}`、契约 DTO 固定放 `.dto` 子包，`vidora-integration` 下是 `org.tiglor.integration.{provider}`）。**这是既有名字，不要改成 `com.vidora` 或 `org.vidora`**；改包名会同时打断 `@SpringBootApplication(scanBasePackages = "org.tiglor")`（见每个服务的启动类）、各 `MybatisPlusConfig` 的 `@MapperScan("org.tiglor.xxx.mapper")` 和 Nacos 上的服务身份。

调用契约的硬规则（落地形态见 `ARCHITECTURE.md` 6.2.1 / 6.2.2）：

1. **契约由被调用方拥有**。A 要调 B，interface + DTO 加进 `vidora-api/vidora-api-B`，**不许在 A 的模块里另抄一份**（`RemoteUserDTO` 原先就抄在 `vidora-video/client/dto`，字段与 `User` 全靠人肉对齐）。新增一个 `vidora-api-{服务名}` 要同时登记 `vidora-api/pom.xml` 的 `<modules>` 和根 `pom.xml` 的 `dependencyManagement`。
2. **契约模块不引 `spring-web` 也不引 `dubbo`**，所以里面不能出现 `@GetMapping` / `@RequestBody` / `@DubboService` 这类注解，接口方法只描述能力。传输层的注解全在两端服务里。
3. **HTTP 契约与 RPC 契约在同一个 `vidora-api-{服务名}` 里并存，不合并**。HTTP 侧是 `XxxApi` + `ApiResult<T>`；RPC 侧是 `RemoteXxxApi` + 裸 DTO。
   原先设想「一份接口将来直接被 Dubbo provider `implements`」，落地时推翻了——`ApiResult` 是 HTTP 响应外壳，让内部 RPC 也裹一层等于要求调用方替一个不存在的浏览器解析状态码。
4. **HTTP 注解落在调用方**：调用方的 Feign 接口 `extends` 契约并重新声明方法（带 `@Override`），签名或返回类型一改就编译失败。**但字段级漂移锁不住**（provider 的出口仍是自己的 `User` 实体，不是 `RemoteUserDTO`），所以注释里**不要**写成「编译期保证字段一致」，只说保证签名。
5. **RPC 契约不抛业务异常，用 `null` 表示「没找到 / 已存在」**，由调用方翻译成 `BizException`。
   原因是 Dubbo 的 `ExceptionFilter`：它只原样透传 checked 异常、方法 `throws` 里声明过的、`java.*` / `javax.*` / `jakarta.*` / `RpcException`、以及与接口同 codebase 的异常。
   `BizException` 在 `common-core`、接口在 `vidora-api-*`，两个 codebase 不同 → 会被拍平成 `RuntimeException(堆栈字符串)`，`code` 全丢。
6. **敏感字段走独立 DTO**。`RemoteLoginUserDTO` 带 `passwordHash`（BCrypt 比对在调用方做，明文密码绝不过网），它和会流到前端的 `RemoteUserDTO` 是两个类。
   实体上的 `@JsonProperty(WRITE_ONLY)` 只挡 HTTP JSON 那条路，挡不住 RPC，所以只能靠类型隔离。

Dubbo 装配的五条注意（都已就地写在代码注释里，别删）：

- `@DubboService` **不是** Spring stereotype，`scanBasePackages = "org.tiglor"` 扫不到，必须让 Dubbo 自己的扫描器认领（`@EnableDubbo(scanBasePackages = "...")`）。
- `@EnableDubbo(scanBasePackages)` 与 yml 的 `dubbo.scan.base-packages` **二选一**，同时配会注册两个 `ServiceAnnotationPostProcessor`。
- `@DubboReference` 由 BeanPostProcessor 注入，只能打在**非 final** 字段上；`@RequiredArgsConstructor` 只覆盖 final 字段，所以同一个类里两种注入方式并存是正常的。
- consumer 侧配 `dubbo.consumer.check: false`，让 provider 不在线时自己也能起来。
- 引 Dubbo 的服务必须一并加 `com.google.code.gson:gson`（`runtime`），否则启动期就挂，原因见 `ARCHITECTURE.md` 第 0 节 #8。**这个坑 `mvn compile` 看不出来。**

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

新加东西先想清楚落在哪一层。**实体归属于唯一读写它的那个服务**：RBAC 那几张 `sys_*` 只在 `vidora-modules/vidora-system` 的 `entity/` 下，别的服务要数据走 `vidora-api-*` 的 RPC 契约（`RemoteUserApi`），**不要共用实体类、也不要复制一份**。历史上 auth 与 system 各有一份逐字节相同的 `User`，先合并成 `common-user` 共用，后来发现「共用一个库」不等于「该共用一份代码」——`common-user` 已整个删除，auth 也不再连库（`ARCHITECTURE.md` 6.2.2）。

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

### 3.1 接口说明：只写 Javadoc，不贴 `@Tag` / `@Operation`

这条是硬约定，理由有两个：一份说明写两处必然漂移（`@Operation(summary=...)` 和注释各说各话），而 Javadoc 同时喂给读代码的人和 `/v3/api-docs`。机制是 RuoYi-Cloud-Plus 那一套，靠两个构件接通（版本与原因见根 pom 的 `therapi.version` 注释）：

| 你写的 | 编译期（`therapi-runtime-javadoc-scribe` 注解处理器） | 运行期（springdoc 的 `SpringDocJavadocProvider`） |
| --- | --- | --- |
| 类级 Javadoc | `target/classes/<包路径>/<类名>__Javadoc.json` | tag 的 description，即这个 controller 在文档里那一组的说明 |
| 方法级 Javadoc 首句 | 同上 | operation 的 **summary** |
| 方法级 Javadoc 其余 | 同上 | operation 的 **description** |
| `@param xxx` | 同上 | 对应参数的 description |
| `@return` / `@throws` | 同上 | 响应的 description / 状态码 |
| VO、Entity、DTO 字段注释 | 同上 | schema 属性的 description（`JavadocPropertyCustomizer`） |

写的时候四条规矩：

1. **Javadoc 必须放在 `@GetMapping` / `@PostMapping` 那组注解之上**，不能贴在方法签名正上方——处理器按声明位置取注释，贴错了等于没写。
2. **endpoint 注释的形状是固定的多行块：第一行接口名，第二行起才写描述。** 第一行是「查询热门标签列表」「标签分页」「新建标签」「提交转码任务」这种动宾结构的接口名，跟 URL 对得上，末尾不加句号；第二行起交代边界（默认给什么档、拒绝什么、幂等还是覆盖、状态机怎么走），**简单接口就不写第二行**。禁止 `/** 一句话 */` 这种单行紧凑式：机制上 springdoc 照样取得到 summary，不会报错，但这份文档是导进 Apifox 当接口清单用的，读的人靠第一行认接口，单行式会把「名字 + 边界 + 坑」挤成一句读不完的长句。
3. **首句就是 summary，其余就是 description**（映射见上表），所以细节、坑、参数语义不要塞进第一行。反例：首句写「这个接口用于查询数据」，导出后整列都是这句废话。
4. **不新增 Swagger 注解**。要覆盖 summary 就改注释本身；`@Operation` 一旦写上，springdoc 以它为准，注释里的同一段说明就变成死字。

```java
// 反例：单行紧凑式，名字和边界挤在一起
/** 上传页的标签联想，按名字前缀匹配 */
@GetMapping("/suggest")

// 正例：第一行是接口名，复杂接口再补一段，参数语义走 @param
/**
 * 查询标签联想列表
 *
 * <p>上传页输入框用，按名字前缀匹配。</p>
 */
@GetMapping("/suggest")
```

这条形状可以机器校验，不用人肉数：

```bash
python .code/tools/check-endpoint-javadoc.py            # 源码：单行紧凑式 / 缺注释 / 首行不是名字
mvn -o clean compile
python .code/tools/check-endpoint-javadoc.py --baked    # 烘出的 JSON：springdoc 真正当成 summary 的那一行
```

两个口径都要 0 问题。`--baked` 比源码口径多一层保障：注释贴错位置（写在注解与签名之间）时源码看着有注释、处理器却取不到，只有 JSON 口径能发现。

验证不用启动服务：`mvn -o compile` 后看 `target/classes/**/*__Javadoc.json` 是否生成（没生成＝处理器没跑到）。字段注释在烘出来的 JSON 里键名是 `doc`，且**没写注释的字段根本不出现在 `fields` 数组里** —— 所以别用 `grep` 数覆盖率，直接问 springdoc 自己的 provider：

```bash
# 依赖 classpath 生成在 target 之外：mvn clean 会连 cp.txt 一起删掉
mvn -o dependency:build-classpath -Dmdep.outputFile=/c/tmp/cp/$(模块名).txt -pl $(模块名)
"$JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp "$(tr -d '\r\n' < /c/tmp/cp/$(模块名).txt)" \
  -d /c/tmp .code/tools/FieldProbe.java
# 编译产物要排在本地仓库那些旧 jar 前面，否则 provider 读到的 BaseEntity 是上个 install 版本，虚报缺描述
"$JAVA_HOME/bin/java.exe" -Dstdout.encoding=UTF-8 \
  -cp "C:/tmp;vidora-common/common-core/target/classes;vidora-common/common-log/target/classes;<模块>/target/classes;$(tr -d '\r\n' < /c/tmp/cp/$(模块名).txt)" \
  FieldProbe org.tiglor.xxx.dto.XxxRequest org.tiglor.xxx.entity.Xxx
```

两个坑都是本机实测踩出来的：单文件源码启动形式（`java C:/tmp/FieldProbe.java`）在 Windows 下会被当成类名解析而失败，必须先编译再按类名运行；`javac` 不带 `-encoding UTF-8` 会按 GBK 读源码直接报「编码 GBK 的不可映射字符」。同理 `java` 要加 `-Dstdout.encoding=UTF-8`，否则中文输出是乱码。类名参数取自 `target/classes` 里的 `*__Javadoc.json` 文件名（去掉后缀、斜杠换成点），一次传整个 `entity`／`dto`／`vo` 包。

现状：全仓 30 个 `@RestController`、123 个 endpoint 的注释都是「第一行接口名 + 可选描述段」的多行块，源码与 `--baked` 两个口径各 123 个、0 问题（`grep -c` 数 `@(Get|Post|Put|Delete|Patch)Mapping` 会得到 125，多出的两个在出口侧的 Feign 接口 `SystemUserClient` 上，不是本服务的 endpoint），`springdoc.enable-javadoc: true` 在 8 个服务的 `application.yml` 里显式写着（默认即 true，写出来是防上游改默认值导致文档静默退化）。

一个已知的例外：`vidora-api/vidora-api-system` 那 7 个类（3 个契约接口 + 4 个 `Remote*DTO`）**注释写全了却烘不出 JSON** —— 同一个模块 `clean compile` 之后 `target/classes` 里一个 `__Javadoc.json` 都没有，而根 pom 的 `annotationProcessorPaths` 对它确实生效（`mvn -X` 能看到 scribe 在处理器路径里）。原因没查出来，别把它读成「注释漏了」，FieldProbe 在那个模块只会报「类找不到 JSON」。实际影响面只有一个：`VideoController.owner` 的响应体 `ApiResult<RemoteUserDTO>` 在 `/v3/api-docs` 里带不出属性描述，其余 `Remote*` 都走 Dubbo，不进 OpenAPI。

字段级是同一套机制的另一半：entity／DTO／VO 的每个实例字段写一行 Javadoc，进 `/v3/api-docs` 就是请求体／响应体里那个属性的描述。内容以 `SQL/vidora_cloud.sql` 的列注释为基准但**不照抄** —— DDL 那边负责档位与含义，Java 这边补 DDL 表达不了的（是否接受调用方传入、由谁填充、null 是什么语义、单位、只读字段）；两者漂移的修法见 12 节。继承来的字段只在父类写一次（`BaseEntity` 四个字段是范本），子类不必重复，springdoc 会沿类层次取。请求 DTO 里那几个 `page` / `size` 之类的公共字段各类各写一份，是因为它们没有共同基类，别为了去重去抽一个 `PageRequest` —— 抽出来会改变 controller 签名与网关参数绑定。已齐平：`mvn -o clean compile` 后用 FieldProbe 逐模块统计 8 个对外模块（content 66 / interact 77 / message 59 / recommend 40 / search 23 / video 72 / system 88 / auth 17，共 442 个实例字段）缺描述均为 0；统计口径只取 `entity`／`dto`／`vo`／`domain` 四个包（漏掉 `vo` 会把 system 数成 57、auth 数成 7），controller 与 service 里 `@Resource` 注入的那些字段不该有属性描述，别把它们算进分母。

**这些注释的读者是 `/v3/api-docs`，不是任何代码生成器。** `mvn -o clean compile` 烘出 JSON，服务起来后 springdoc 把它们填进 `/v3/api-docs`（网关侧不做聚合，逐服务直接访问；导入 Apifox 由人工执行）。描述写全的唯一判据就是「文档里那一格不是空的」—— 三端的类型文件不从这里生成。

三端的契约类型是**各自手抄的副本**：admin `src/api/types.ts`、mobile `src/types/index.ts` 各一份 TS interface，web 是纯 JS，形状只活在 `src/api/*.js` 每个函数上方的注释里。所以：

- 动了 controller 签名、DTO／VO／实体的字段或注释，**三端不会编译报错**。交付说明里必须点名「哪个字段改了名 / 哪一端的哪个类型文件要跟着改」，不能只写「接口加好了」。
- 判据来自 `SQL/vidora_cloud.sql` 的列注释（档位与可空性）加 Java 侧的字段 Javadoc（是否接受传入、由谁填充、null 的语义）；下游要核对就直接读那两个源，别信自己记忆里的那个名字。

## 4. Service / Mapper / Entity / DTO / VO 各自放什么

| 层 | 放什么 | 不放什么 | 出处 |
| --- | --- | --- | --- |
| `service/`（接口） | `extends IService<Entity>`；只声明本域业务方法与缓存语义 | 不暴露 Mapper 细节 | `content/service/CategoryService.java` |
| `service/impl/` | 业务规则、完整性校验、事务、缓存注解、幂等处理 | 不读 `HttpServletRequest`、不拼 HTTP 状态码 | `CategoryServiceImpl` |
| `mapper/` | `interface XxxMapper extends BaseMapper<Entity>` + `@Mapper`；**例外：`common-log` 的 `OperLogMapper` / `LoginLogMapper` 刻意不标也不自我装配**，由 system-service 的 `config/MybatisPlusConfig.java` 用多包扫描接管 —— `@MapperScan({"org.tiglor.system.mapper", "org.tiglor.common.log.mapper"})`（该文件注释解释了原因）。别的服务不要照抄这个例外 | —— | `vidora-modules/vidora-system/.../system/mapper/MenuMapper.java`、`system/config/MybatisPlusConfig.java` |
| `entity/` | 与表列一一对应 | 不放展示字段、不放校验注解 | `content/entity/Category.java` |
| `dto/`（入参） | 外部可写的字段 + 校验注解 | **绝不放 `id` / `createTime` / `isDeleted`** | `content/dto/CategoryRequest.java` |
| `dto/`（投影） | 树节点、聚合计数这类「不是表但要有形状」的输出 | 不加持久化注解 | `CategoryNode`、`ActionTypeCount` |
| `vo/` | 需要给 entity 补展示字段时，**继承 entity 再加字段** | 不重抄一遍字段列表 | `system/vo/OperLogVO extends OperLogEntity` |

三条硬规则及其理由（都是文件里已有的注释）：

1. **写入一律收 Request 对象，不收裸实体**。`CategoryController` 顶部注释：裸实体能让调用方自己填 id、createTime，一次「新增」就变成对任意行的覆写。
2. **VO 继承 entity 而不是复制字段**。`OperLogVO` 注释：这两份必须逐字段对齐，抄一份就是留一处漂移。昵称这类「表里没有、要现补」的字段加在子类上，并注明来自同库批量查询。
3. **自定义 SQL 写在 Mapper 上用注解 + 文本块**，不建 XML。全仓 **0 个 `mapper/*.xml`**（虽然 yml 里配了 `mapper-locations: classpath*:/mapper/*.xml`），复杂查询见 `interact/mapper/InteractActionMapper.countByTarget` 的 `@Select("""...""")`，注释里要写清走哪个索引、为什么值得省一次往返。

Entity 两种写法，按表决定，不要混：

- 表有 `create_time/update_time/is_deleted` → `extends BaseEntity`（`common-core/.../BaseEntity.java`：`@TableId(type=IdType.AUTO)` + `@TableField(fill=...)` + `@TableLogic`），类上 `@Data @EqualsAndHashCode(callSuper = true) @TableName("xxx")`。例：`vidora-modules/vidora-system/.../system/entity/User.java`。
- 表没有 `is_deleted`（如 `content_category` 靠 `status` 下架）→ 自己声明 `@TableId` + 两个 fill 字段，**不继承 BaseEntity**，并在类注释里写明为什么不继承。例：`content/entity/Category.java`。这 19 个实体重复声明 id／时间字段不是没收拾干净的重复代码，别顺手合并进 `BaseEntity`：各服务 yml 的 `mybatis-plus.global-config.db-config.logic-delete-field: isDeleted` 是**按属性名**生效的，实体一旦带上 `isDeleted`，MyBatis-Plus 就在每条查询后追加 `is_deleted = 0`，无此列的表立刻 SQL 报错（`Unknown column 'is_deleted' in 'where clause'`）。

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
- **dev profile 才打开 SQL 日志**，生产不开。理由写在 content/system 的 yml 尾部注释里：生产把语句连参数打进日志，等于把业务数据多复制一份。日志包名要跟对：普通服务是 `org.tiglor.{service}.mapper`（system-service 的 RBAC Mapper 也在这，没有例外），查审计落库另加 `org.tiglor.common.log.mapper`。
- 新增自定义配置项：写成 `@ConfigurationProperties` 类（参考 `common-log/.../config/LogProperties.java`，前缀 `vidora.log`，含 `path` / `oper-enabled` / `ingest-enabled` / `ingest-service-id`(默认 `system-service`) / `ingest-path`(默认 `/internal/logs`) / `ingest-token` / 长度上限等），带默认值和注释；不要散 `@Value` 在各处。
- Nacos 只用于注册发现。要加配置得改这里的 yml，**别指望 Nacos 控制台**。

## 8. 日志规范

地基：`vidora-common/common-core/src/main/resources/logback/logback-base.xml`，由各服务 `logback-spring.xml` 一行 `<include>` 引入。**要改日志策略改这一份，不要在各服务复制。**（放在 common-core 而不是 common-log 的原因写在文件头注释：网关排掉了 web starter，不能为一个 XML 依赖 common-log。）

产物固定在 `logs/<spring.application.name>/`（相对进程 cwd）：本地 `mvn -pl ... spring-boot:run` 的 cwd 已由根 pom 里 spring-boot-maven-plugin 的 `workingDirectory` 归一到仓库根，docker 里由 `VIDORA_LOG_PATH=/app/logs` 覆盖，两种跑法落点形态一致：

起单服务用 `mvn -pl <模块> spring-boot:run`，**别带 `-am`**：`-am` 会把根聚合 pom 也选进 goal 列表，run 在根上直接报 `Unable to find a suitable main class`；要先刷新依赖就分开两步（`-pl <模块> -am compile` 之后再单独 run）。

| 文件 | 内容 | 滚动 |
| --- | --- | --- |
| `app.log` | INFO 及以上，排查主现场 | 天 + 大小（默认 100MB / 30 天 / 5GB 上限），gz |
| `error.log` | 只有 ERROR（LevelFilter） | 保留 90 天 |
| `audit.log` | logger 名 `AUDIT`，`additivity=false`，一行一条 JSON。**是本地副本，不是审计的存储位置** | 留 365 天 |

`audit.log` 留 365 天的理由和上面两个文件不一样：审计的正主是库表 —— `@OperLog` → `LogSink` → system-service 的 `sys_oper_log` / `sys_login_log`，管理端「日志管理」查的是那两张表。`RemoteLogSink` 是**先落文件、再异步上报**（上报线程池可能拒绝任务、system-service 可能不通，文件这一份才是确定性的），所以每条审计记录这里都会有一行，**不是只在失败时才写**。它的用途是补录：文件里有、库里没有，说明上报或落库那条路断了 —— 照「表建了没有（`SQL/vidora_cloud.sql` 第二节审计段的 CREATE TABLE）→ system-service 起没起 → Nacos 解析到没有 → ingest 口令符不符」这个顺序查，别去翻 logback。

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

1. **契约陷阱**：null 语义、字段来自另一个服务的表、tinyint 不是布尔、单库了也照样不给跨节外键（`Category` 类注释）。
2. **安全与合规取舍**：为什么这个接口不打审计、为什么这个路径 permitAll、为什么这张表不给删除口（`OperLogController`、`SecurityConfig`、`SQL/vidora_cloud.sql`「二、审计日志」节头部）。
3. **版本迁移坑**：Boot 4 / MP 3.5.17 造成的非常规写法，如 `RemoteLogSink.post` 里「用字符串做请求体，绕开 Boot 4 下 Jackson2 / Jackson3 两套转换器并存的选型问题」。

数字枚举一定要写档位（`business_type` 0-其他…9-状态变更、`risk_level` 0-无…3-高），DDL 列注释和 Java 注释两处都要有。

强调一律用「」，不写 markdown 的 `**X**`：这些注释经 therapi 进 `/v3/api-docs`，Apifox / Postman 那侧不一定渲染 markdown，星号就成了字面噪音。

Controller 上的类级和方法级 Javadoc 不只是给人看的，它们就是接口文档本身，写法与验证见 3.1。

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

## 12. SQL 与数据库变更

完整流程见 `.code/skills/add-a-db-migration.md`，这里只列硬约束。

- **一个业务库、一个文件，文件内按业务功能分节。** `SQL/vidora_cloud.sql` 是 `SQL/` 下唯一的文件：库名 `vidora_cloud`，33 张表分 8 节（用户与权限 / 审计日志 / 视频与转码 / 内容运营 / 互动 / 站内消息 / 搜索 / 推荐），节序就是依赖序，表名前缀与节名一一对应；全文件恰好一条 `CREATE DATABASE` + 一条 `USE`。原先「一个微服务一个 schema、七个文件」（`user_service`…`recommend_service`）由用户决定收口；`08_` 起放 `NN_migration_{主题}.sql` 给存量库打补丁的约定**同时作废** —— 迁移与基线两份内容逐字重复、各改各的，几周后没人判断得出哪份是准的。新表进它所属业务那一节，不另开文件、不新开库。
- **建表必须带描述信息，没有例外。** 表级 `) ENGINE=InnoDB ... COMMENT='...'`，列级每一列都要 `COMMENT '...'`，包括 `id` / `create_time` / `update_time` / `is_deleted` 这四个看起来不用解释的。枚举列的注释要把档位逐个写出来（`sys_oper_log.business_type` 是范本），因为 Java 侧枚举常量和三端下拉文案以它为第二处出处。`sys_client` 建表时漏了四列注释，在库里空了很久才被发现，最后靠 `MODIFY COLUMN` 回填 —— 别再制造第二次。
- **改了列注释，就要同步改实体字段的 Javadoc。** 自接口文档改走 Javadoc 起，字段语义有了第三处出处：entity／DTO 的字段注释经 therapi 变成 `/v3/api-docs` 里的 schema 属性描述（机制见 3.1）。DDL 改了而 Java 没改，接口文档会继续讲旧档位，而且没有任何编译错误提示你。核对两处是否齐平：`mvn -o compile` 后看 `target/classes/**/*__Javadoc.json` 里该字段的 `doc` 是否为空（没写注释的字段压根不出现在 `fields` 数组里，所以数覆盖率要用 3.1 的 FieldProbe，不能 grep），别只信 `git diff`。写在 Java 侧而 DDL 没有的信息（谁维护、null 语义、单位、是否接受调用方传入）留在注释里，那是 DDL 表达不了的。
- **文件只写目标态，不在里面追加 `ALTER`；建库建表用普通 DDL，不写 `IF NOT EXISTS`。** 改结构就改那张表 `CREATE TABLE` 里对应的一行。`IF NOT EXISTS` 的语义是「已存在就静默跳过、整条当成功」，于是列定义改了、库没变、执行的人以为生效了 —— 这个错觉是本仓库明确不要的行为，所以普通 DDL 遇到已有库直接报错停下（`ERROR 1050 Table 'x' already exists`）。推论：`vidora_cloud.sql` 是全量建库脚本，不是可重跑的补丁；重建 = `DROP DATABASE vidora_cloud;` 后重跑，不方便重建的靠手工执行等价 `ALTER` —— 交付里要把这几条 `ALTER` 写出来，工程里没有 Flyway/Liquibase 替你算。只有种子数据保留 `INSERT IGNORE`（DML 幂等，与 DDL 无关）。
- **种子数据写死主键 id**，不靠 AUTO_INCREMENT：三端按 id 引用分区与菜单（`vidora-web` 引用分区 1–7，`sys_role_menu` 以 `sys_menu.id` 为锚点），自增值受插入与删除历史影响会整体错位。
- **`SQL/vidora_cloud.sql` 默认由用户手工执行**，AI 不连库跑 DDL；只有用户在当前会话里点名才可以跑（`.code/agent-rules.md` 第四节）。工程里没有 Flyway / Liquibase，docker 的 `initdb.d` 挂载也只在数据目录为空时生效一次。所以「代码可以先于 DDL 落地合并」，这类改动交付时要标为**未验证 —— 等待 DDL 执行**。
- 字符集与排序规则一律 `utf8mb4` / `utf8mb4_unicode_ci`（compose 里 MySQL 启动参数同此）。换 `_bin` / `_cs` 会改变「同一父分类下重名」的判定行为。
- **不建外键，跨节引用同理。** 合库之后 MySQL 技术上支持外键了，但仍然禁止：跨服务外键会把服务边界焊成部署耦合（拆库、改名、按租户分库都要先解锁约束）。库级隔离没了，边界靠两条软约束守 —— 表名前缀就是归属、一个服务的 Mapper 只碰自己那一节；要别人的数据走 `vidora-api` HTTP 契约或 Dubbo，不写跨节 join。引用完整性照旧在 service 层守，并在 entity 注释里写明这个限制（`Category` 类注释是范本）。
- 改完 SQL 要跑一遍注释自检（命令在 skill 第 2 步），并核对行尾：`SQL/vidora_cloud.sql` 是 **LF**（合库时新建的文件），别整文件格式化；判据用字节计数（`tr -dc '\r' < f | wc -c`），不要用 `grep -cU $'\r'`（本机 Git Bash 下它对纯 LF 文件也报满屏命中）。
