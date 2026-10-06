# Skill：新增一套 CRUD 接口

适用：给某个已有资源域加完整的增删改查出口（像分类、标签那样）。如果只是给现有 Controller 加一个方法，跳过第 5–7 步。

前置阅读：`.code/coding-standards.md` 第 1/3/4/5/6 节、`.code/requirements.md` 第二节。

参照样本（照抄结构，不要自创）：`vidora-modules/vidora-content/src/main/java/org/tiglor/content/` 下的 `entity/Category.java`、`mapper/CategoryMapper.java`、`dto/CategoryRequest.java`、`service/CategoryService.java`、`service/impl/CategoryServiceImpl.java`、`controller/CategoryController.java`。

---

## 步骤

### 1. 确认归属服务与它管的表

打开 `.code/requirements.md` 第二节的表，按「谁写这张表」定服务。库只有一个（`vidora_cloud`），所以要现场确认的是这个服务的端口，以及它负责的那一节里的表前缀：

```bash
grep -nE "port:|name:|url:" vidora-modules/vidora-content/src/main/resources/application.yml
```

**验证点**：表前缀就是归属，一个服务的 Mapper 只碰自己那一节的表；合库之后 MySQL 技术上支持外键了，但照样不给跨节建外键（那是把服务边界焊成部署耦合），引用完整性只能靠 service 层守。

### 2. 先写 DDL，再写代码

在 `SQL/vidora_cloud.sql` 对应业务那一节里加 `CREATE TABLE`（普通 DDL，**不带 `IF NOT EXISTS`**）—— **新表进它所属业务那一节，不另开文件、不新开库**（约定与理由见 `.code/skills/add-a-db-migration.md`）。列注释必须写枚举档位，因为那就是语义的第一手出处。

约定：主键 `BIGINT UNSIGNED AUTO_INCREMENT`；通用列 `create_time` / `update_time`（DEFAULT CURRENT_TIMESTAMP，后者 ON UPDATE）；有 `is_deleted` 才走逻辑删除。

**验证点**：DDL 里的列名 snake_case 与 entity 字段 camelCase 能对上（yml 里 `map-underscore-to-camel-case: true`）。

### 3. Entity

`{service}/entity/Xxx.java`：

```java
/** 一句话说明这张表是什么 */
@Data
@TableName("xxx_table")                     // 表名带域前缀，如 content_category
public class Xxx extends BaseEntity {       // 表有 is_deleted 才继承
    private String name;
    /** 状态：0-禁用 1-启用 */
    private Integer status;
}
```

两种写法二选一，并在类注释写明理由（参考 `Category` 的注释：表上没有 `is_deleted`，下架靠 `status`，所以不继承 `BaseEntity`）：

- `extends BaseEntity`（`common-core/.../BaseEntity.java` 已给 `id` + 两个 fill 字段 + `@TableLogic`），类上加 `@EqualsAndHashCode(callSuper = true)`。例：`vidora-modules/vidora-system/.../system/entity/User.java`。
- 自己声明 `@TableId(type = IdType.AUTO)` + `@TableField(fill = FieldFill.INSERT/INSERT_UPDATE)` 的 create/updateTime。

敏感列加 `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)`。

**禁止**：在 entity 上放校验注解、放展示字段、放 id 以外的「不该由外部决定」的可写字段。

### 4. Mapper

```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {
}
```

简单 CRUD 到此为止 —— **全仓没有任何 `mapper/*.xml`**，不要新建 XML。需要自定义 SQL 时用注解 + 文本块，并在 Javadoc 写清走哪个索引：模板见 `vidora-modules/vidora-interact/src/main/java/org/tiglor/interact/mapper/InteractActionMapper.java`。

**验证点**：`@MapperScan` 已覆盖该包（每个服务的 `config/MybatisPlusConfig.java` 里写死了 `org.tiglor.{service}.mapper`），新 Mapper 放在别的包下会静默扫不到、启动时才报 NoSuchBean。

### 5. Service 接口 + Impl

接口：

```java
public interface XxxService extends IService<Xxx> {   // 注意包路径，见下
    List<Xxx> listEnabled();
    Page<Xxx> page(long current, long size, Integer status);
    Xxx create(XxxRequest request);
    Xxx update(Long id, XxxRequest request);
    void setStatus(Long id, int status);
    void delete(Long id);
}
```

Impl：

```java
@Service
public class XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx> implements XxxService {
```

**必踩的两个坑**：

```java
// ✗ MP 3.5.17 之后这两个路径已经不存在，IDE 自动补全会给你错的
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

// ✓ 本仓库全部 41 处用的是这个包
import com.baomidou.mybatisplus.spring.service.IService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
```

实现要点（都有现成范本，照抄）：

- 校验私有方法统一命名 `requireExists` / `requireNameAvailable` / `normalizeXxx` / `clampSize`，失败抛 `BizException(ResultCode.X, msg)`。见 `CategoryServiceImpl` 尾部。
- 分页上限在 service 层夹（`MAX_PAGE_SIZE = 100`），别信前端传的 size。
- 排序补一个兜底键（`orderByAsc(getSortOrder).orderByAsc(getId)`），否则同序值翻页会抖。
- 「不存在」→ `throw new BizException(ResultCode.NOT_FOUND, ...)`，**不是返回 null**。
- 需要缓存就在读方法 `@Cacheable(cacheNames = CacheNames.XXX, key = "'...'")`，写入侧 `@CacheEvict(allEntries = true)`；缓存名先在 `common-redis/.../CacheNames.java` 加常量。同类内部自调用不走代理，`@Cacheable` 会静默失效。

**验证点**：写完立刻编译 —— 包路径错误只有编译器能抓到。

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile
```

### 6. 入参 DTO

`{service}/dto/XxxRequest.java`，带 jakarta validation 且 message 是给人看的中文：

```java
/**
 * 新增 / 修改 Xxx。
 * id 不在这里：新增时由数据库分配，修改时取路径上的 {id}。
 * 放进请求体的话调用方就能通过改 id 把一次「修改」变成对另一行的覆写。
 */
@Data
public class XxxRequest {
    /** 名称，纯空白按空处理；唯一性口径照 DDL 的 uk_* 写清楚（是全局唯一还是同一父级下唯一） */
    @NotBlank(message = "名称不能为空")
    @Size(max = 50, message = "名称不能超过 50 字")
    private String name;

    /** 状态：0-禁用 1-启用；不传时新增按 1 落库，修改时保持原值不动 */
    @Min(value = 0, message = "status 只能是 0 或 1")
    @Max(value = 1, message = "status 只能是 0 或 1")
    private Integer status;
}
```

命名沿用主流 `XxxRequest`（auth 模块的 `XxxDTO` 是个例外，不要在新增模块里扩散）。

**每个字段都要有一行 Javadoc**，它进 `/v3/api-docs` 就是那个属性的 description，Apifox／Postman 导入后直接显示；空着的地方在导入结果里就是一个空洞的说明栏。内容口径见 `coding-standards.md` 3.1 与 12 节：以 `SQL/vidora_cloud.sql` 的列注释为基准，再补 DDL 表达不了的（是否接受调用方传入、不传时的行为、null 语义）。

### 7. Controller

```java
/**
 * {资源}管理。读取只要登录，增删改需要 {@code {域}:{资源}:manage}。
 * <p>
 * 这里要写清这一层的边界：口径是什么、什么走别的接口、为什么没有某个口。
 * </p>
 */
@RestController
@RequestMapping("/xxxs")                 // 复数、无 /api 前缀、kebab-case
@RequiredArgsConstructor
public class XxxController {

    private final XxxService service;

    /**
     * 查询启用{资源}列表
     *
     * <p>走缓存，给下拉选择器用；被禁用的不在这里，管理端要看全量走下面的分页口。</p>
     */
    @GetMapping("/list")
    public ApiResult<List<Xxx>> listEnabled() { return ApiResult.ok(service.listEnabled()); }

    /**
     * 管理端的{资源}分页：按 {@code sort_order} 升序，同一父级下的排在一起。
     * <p>
     * 查的是整张表，被禁用的也看得见——否则运营没法把它改回来。
     *
     * @param status 0-禁用 / 1-启用；不传时两种都列
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('{域}:{资源}:manage')")
    public ApiResult<Page<Xxx>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, status));
    }

    /**
     * 新建{资源}
     *
     * <p>父级必须已存在，同一父级下不能重名。</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('{域}:{资源}:manage')")
    @OperLog(title = "Xxx管理", type = BusinessType.INSERT)
    public ApiResult<Xxx> create(@Valid @RequestBody XxxRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/{id}")            // 更新走 PUT + 路径 id
    @DeleteMapping("/{id}")
    @PutMapping("/{id}/status")     // 上下架单独一个口，幂等
}
```

每个 endpoint 的 Javadoc **第一行只写接口名**（「查询xx列表」「xx分页」「新建xx」这种动宾短语，末尾不加句号），它就是接口文档里的 summary；第二行起用 `<p>` 写边界，简单接口就不写第二行，`@param` 进参数说明、VO/Entity 字段注释进 schema 属性说明。**禁止 `/** 一句话 */` 单行紧凑式，不贴 `@Tag` / `@Operation`**，注释必须放在 `@GetMapping` 那组注解**之上**。写法与两个口径的机器校验见 `coding-standards.md` 3.1（`python .code/tools/check-endpoint-javadoc.py`）。

REST 约定（对齐现有 Controller）：

| 动作 | 形式 |
| --- | --- |
| 查列表 / 查分页 | `GET /list`（不分页）、`GET /page?current=&size=` |
| 查单条 | `GET /{id}` |
| 新增 | `POST` （无子路径） |
| 修改 | `PUT /{id}` |
| 状态流转 | `PUT /{id}/status?status=` |
| 删除 | `DELETE /{id}` |

**验证点**：URL 全程不带 `/api` —— 那是网关加的，写了会变成 `/api/api/xxxs`。

### 8. 网关路由（**每一步都要做，漏了就是 404**）

编辑 `vidora-gateway/src/main/resources/application.yml`：

- 复用某服务已有的那条 route → 把新前缀**追加进它的 `Path=` 列表**（逗号分隔），例：`Path=/api/categories/**,/api/tags/**,...`
- 全新前缀且属于既有服务 → 可新开一条 route，`uri: lb://{service}-service` + `filters: - StripPrefix=1`

若这个接口只给管理端用，**停下来转 `.code/skills/add-admin-only-endpoint.md`**，那里还有三处要同步。

**验证点**：YAML 缩进必须和相邻条目完全一致（`routes` 是列表项，多一个空格就整段解析不上）。行尾：`vidora-gateway/src/main/resources/application.yml` 实测是 LF，不要用 CRLF 工具改它。

### 9. 编译

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-{service} -am compile
mvn -o -Dmaven.legacyLocalRepo=true compile      # 改了 gateway 或 common-* 时跑全仓
```

预期退出码 0；`jansi` / `sun.misc.Unsafe` 的 WARNING 来自 Maven 自身，忽略。

### 10. 通知下游：三端的类型是手抄副本

新接口在编译过的那一刻只完成了一半：admin `src/api/types.ts`、mobile `src/types/index.ts`、web `src/api/*.js` 上方的注释都是人工维护的副本，**后端加接口不会让任何一端报错**。所以这一步写在交付里，不是跑命令：

- 点名服务、HTTP 方法与路径，以及它的请求／响应类（`XxxRequest` / `XxxVO`）和关键字段的档位来源（哪一列、DDL 注释怎么说、null 是什么语义）。
- 服务起着时核对描述是否真带出来了：`GET http://127.0.0.1:{服务端口}/v3/api-docs` 看目标类的 `properties`。导入 Apifox 由用户手动执行，本仓库不做网关聚合。
- 三端各自接口的步骤在对应端的 `.code/skills/add-or-adjust-api-call.md`，不要在 vidora-cloud 里替它们改文件。

纯后端内部实现（不改出入参形状、不改注释）不用带这一条。

### 11. 运行时验证（需用户同意起服务）

按 `.code/skills/verify-a-backend-change.md` 走 curl 四连：正常创建、参数非法（400）、未登录（401）、目标不存在（404）。迁移未执行时会拿 500/表不存在 —— 那是**未验证**而不是 bug，如实标注。

---

## 常见坑

1. **`IService` / `ServiceImpl` 导错包**。MP 3.5.17 把它们从 `extension.service` 挪到了 `spring.service`。这是新会话最高频的一次性失败。
2. **忘加网关路由**。Controller 编译过、服务起得来，但外部一律 404。判定顺序是先路由后鉴权，所以这条比权限问题更早暴露。
3. **`StripPrefix` 理解错**。`Path=/api/xxxs/**` + `StripPrefix=1` 剥掉的是 `/api` 这一段，所以 Controller 必须写 `/xxxs`。
4. **把裸实体当入参**。调用方能填 `id` / `createTime`，一次「新增」变成覆写任意行。必须收 Request 对象。
5. **业务失败返回 200**。要么手动 `ApiResult.error(...)` 要么 catch 住异常 —— 两者都会让监控里全是假成功。抛 `BizException`，让 `GlobalExceptionHandler` 去映射 HTTP 状态。
6. **「不存在」返回 null**。前端第一个 TypeError 就在这。区分清楚：对象该存在却查不到 → 404；关系本来可以不存在 → 允许 null 但 Javadoc 写明（`SecurityAuditController.findByTarget` 是后者的范本）。
7. **分页返回 `PageResult`**。`common-core/.../PageResult.java` 零引用，实际契约是 MP 的 `Page`（`records/total/size/current/pages`）。用了 `PageResult` 等于给三端制造新的形状不一致。
8. **新建 `mapper/Xxx.xml`**。本仓库没有 XML mapper，虽然 yml 配了 `mapper-locations`。自定义 SQL 用 `@Select` 文本块。
9. **缓存名硬编码字符串**。必须先进 `CacheNames`，跨服务共用一个 Redis，靠缓存名前缀避免键冲突。
10. **给高频用户流水加 `@OperLog`**。评论、点赞、播放计数是业务流水，量级差两个数量级，混进审计表会把真正要查的淹掉（`common-log/.../annotation/OperLog.java` 类注释就是这条判据）。
11. **顺手统一行尾或格式化**。同为服务 pom，`vidora-modules/vidora-system/pom.xml` 是 LF 而 `vidora-auth/pom.xml` 是 CRLF；`SQL/vidora_cloud.sql` 是 LF 而根 `README.md` 是 CRLF。逐文件不同，只改动需要的那几行。
12. **改了 common-\* 只编下游模块**。公共模块不会自动重编，用 `-am` 或全仓 `compile`。
