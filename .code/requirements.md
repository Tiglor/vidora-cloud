# 需求规范（requirements）

本文件管的是「需求怎么写、边界在哪里、什么算做完」。代码层面的约定看 `coding-standards.md`。

---

## 一、这个仓库在产品里的定位

vidora 是视频播放平台，四个工程：

| 工程 | 角色 | 使用者 |
| --- | --- | --- |
| `vidora-cloud`（本仓库） | Spring Cloud 后端，**接口契约与业务口径的唯一权威源** | 三端 + 服务账号 |
| `vidora-web` | 桌面浏览器消费站 | 观众 + 访客 |
| `vidora-mobile` | uni-app 移动端 / H5 / 小程序 | 观众 |
| `vidora-admin` | 运营管理后台 | 内部管理员，接口被网关按 clientKey 锁定 |

本仓库拥有（后端说了算的部分）：

- **数据与表结构**：单库 `vidora_cloud`，全量 DDL 与种子就在 `SQL/vidora_cloud.sql` 一个文件里、按业务功能分 8 节，列注释就是字段语义的第一手出处。
- **业务规则与统计口径**：计数怎么算、层级能不能成环、重名是否允许、状态机怎么流转 —— 全在 `service/impl`，前端只读结果。
- **认证与授权**：JWT 签发（auth-service）、网关校验与身份透传（`GatewayAuthFilter` + `SecurityHeaders`）、接口级权限（各服务 `@PreAuthorize`）。
- **审计与可观测性**：`sys_oper_log` / `sys_login_log`、traceId 链路、分级日志文件。
- **对外契约本身**：路径、方法、参数名、返回形状、错误码与 HTTP 状态的对应关系。

本仓库**不做**：页面渲染、交互反馈、本地会话存储、主题（后端只存 `theme_key` 字符串不解释含义，见 `vidora-modules/vidora-system/.../system/entity/User.java` 的注释）、按钮可见性判断。

## 二、加一个能力该落在哪个服务

先看数据在哪张表、谁写这张表，再定服务。**不要为了「前端想在一个接口里拿到全部」而把别的域的写口搬过来**；跨域聚合的正确做法是提供批量读接口（已有 `POST /api/videos/batch`，`VideoController.batch`，id 上限夹 50），让调用方自己拼。

| 能力属于 | 服务（端口） | 表在 `vidora_cloud` 库的位置 | 判据 / 现有出口 |
| --- | --- | --- | --- |
| 注册、登录、token 签发、用户自助偏好（改主题） | auth-service (8101) | 不连库，经 Dubbo 向 system-service 取 | `/auth/**`、`/profile/**` |
| RBAC：用户、角色、菜单、客户端配置；审计日志落库与查询 | system-service (8108) | `sys_*` / `user_*`（第 1 节）+ 第 2 节两张 `sys_*_log` | `/users` `/roles` `/menus` `/clients` `/oper-logs` `/login-logs` + `/internal/logs` |
| 视频本体：元信息、上传/分片、存储、转码任务、播放与下载地址 | video-service (8102) | `video_*`（第 3 节） | `/videos/**` |
| 字典与运营位：分类、标签、推荐流配置、热搜榜、内容安全审核台 | content-service (8103) | `content_*`（第 4 节） | `/categories` `/tags` `/feed-configs` `/hot-searches` `/security-audits` |
| 互动流水：评论、点赞收藏分享、弹幕、播放计数 | interact-service (8104) | `interact_*`（第 5 节） | `/comments` `/actions` `/danmaku` `/play-counts` |
| 站内消息、私信会话、推送设备绑定 | message-service (8105) | `message_*`（第 6 节） | `/messages` `/conversations` `/push-devices` |
| 搜索历史、词频统计、建议词字典 | search-service (8106) | `search_*`（第 7 节） | `/search/**`（三个控制器都挂在 `/search` 下） |
| 推荐候选、算法配置、用户画像特征 | recommend-service (8107) | `recommend_*`（第 8 节） | `/recommends` `/algo-configs` `/user-features` |
| 路由、鉴权、身份透传、traceId 起点 | gateway-service (8080) | 无表 | 不写业务代码，只碰 filter 与 yml |

归属拿不准时的三条判据：

1. **写口的归属优先于读口**。热搜榜由 content 写、search 只贡献词频，所以「运营调热搜顺序」是 content 的事。
2. **合库之后「能不能 join」不再是判据**，判据是「谁写这张表」。审计要显示昵称，`sys_oper_log` 和 `sys_user` 现在同在一个库里、join 技术上可行，但依然由查询侧按 id 批量补昵称、不落冗余列（`SQL/vidora_cloud.sql`「二、审计日志」节头注释解释了为什么不冗余）。同库不等于可以直读别人的表。
3. **新表进它所属业务那一节，绝不塞进别人的节**。单库之后仍然不建外键：跨服务外键会把服务边界变成部署耦合，引用完整性照旧靠 service 层守（`Category` 类注释就写了这条限制）。

两个不属于任何单个服务的落点：

| 要加的东西 | 落在哪 | 判据 |
| --- | --- | --- |
| A 同步调 B 的接口与 DTO | 契约进 `vidora-api/vidora-api-B`（**B 拥有**），A 只在 `client/` 写 Feign 实现 | 对端与本仓库同一次发布 → 内部契约 |
| 对接平台外部系统（云转码、内容安全、短信、支付、CDN） | `vidora-integration`，OpenFeign 留在这条线上 | 对端在平台之外 → 必须在本层把外部模型转成内部模型，别让外部 DTO 漏进业务服务 |
| 不要求立即返回的跨服务动作 | RocketMQ 事件，不要新开一条同步调用 | 见 `ARCHITECTURE.md` 6.2.1 / 6.4 |

「响应慢，要不要换 Dubbo」不是一个需求，是一个结论：先说清**哪一次请求跨了几次服务**，再按 6.2.1 的触发条件判断。当前全仓只有 1 条业务同步调用（`VideoController.owner`），首屏 / 详情 / 评论 / 搜索都在自己库里出，Dubbo 没有可优化的链路——真要做的是服务端聚合（BFF），那是跨端契约变更，得先拍板。

### 边界纪律（越界前停下问用户）

- 前端提「你们加个字段吧」「这两个接口合成一个吧」——这是跨端变更，影响 web / mobile / admin 三个仓库，**由用户拍板**，AI 不得自行答应并实施。
- 「资源不存在」不能退化成 200 + null（本轮已修过一次）；但确实可为空的关联查询保留 null 且必须写 Javadoc。这两种语义的区别要在需求里问清楚，别自己猜。
- 权限位（`sys_menu.permission_code`）新增意味着管理端要出配置界面、两端要改可见性 —— 一次改四处的需求，先确认范围。
- 三端各自的展示问题不在本仓库解决。发现前端硬编码了本该由后端提供的数据，正确动作是**提供出口 + 通知对应端**，而不是在后端猜一份默认值。

---

## 三、需求怎么写：四段式

接到口头需求（尤其来自 AI 会话或随手一句话）时，先把下面四段填出来再动手。填不出「范围」和「验收标准」就不要开始写代码。

```markdown
## 背景
当前行为是什么、造成什么问题、谁受影响。
（例：管理端删除分类时，若该分类仍被子分类引用，请求会成功并留下悬空 parent_id，
 树形接口那一支直接消失，没人能再把它改回来。）

## 范围
做什么：落到具体服务、类、SQL 文件。
不做什么：显式列出容易被顺手带上的东西。
（例：只动 content-service 的 CategoryServiceImpl + 一条 SQL；
 不动数据库约束（跨库做不到）；不给前端加新出口；不改 delete 的路径与权限码。）

## 约束
- 契约以后端哪个方法为准：XxxController.yyy 的签名不许变（三端在调）。
- 鉴权边界：读接口只要登录；写接口需 {域}:{资源}:{动作} 权限码，
  且路径必须在 GatewayAuthFilter.ADMIN_PATH_PREFIXES 内（若是管理端专用）。
- 数据：涉及 SQL/vidora_cloud.sql 的结构变更，由用户手工执行，本次交付不含执行结果。
- 兼容性：分页参数沿用 current/size；返回 Page<T>；不引入 PageResult。
- 不能碰的既有约定：ApiResult 外壳、GlobalExceptionHandler 的状态码映射、logback-base.xml。

## 验收标准
- 编译：mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile 退出码 0。
- 正常路径：curl POST /api/categories（admin token）→ 200，data.id 是新分配值。
- 失败路径：父级不存在 → 400 且 message 指出具体 id；未登录 → 401 无 body；
  普通用户 token → 403；缺权限码 → 403。
- 审计：logs/content-service/audit.log 出现一行 kind=oper、business_type=3 的记录，
  sys_oper_log 里可查到同 trace_id。
- 缓存：删除后 GET /api/categories/list 不再返回该项。
- 未验证项如实标注（如服务未启动则 curl 一组全标未验）。
```

反面例子（不该直接开工的需求写法）：「优化一下日志」「把推荐做准」「这里顺便加个接口」。这类描述既没有范围也没有验收标准，做出来的东西没人能判断对不对。

---

## 四、契约以什么为准

**契约由 Controller + DTO/entity/VO 定义，不是由前端的类型文件或 api 层注释定义。**

理由：三端各自抄写了一份后端形状（mobile 有手写 `src/types/index.ts`，web 靠 `src/api/*.js` 上方的人工注释），人工抄的东西一定会漂 —— 已发生过的事故包括 `HotSearch.heat` 实际叫 `heat_score`、`Category` 用 `sortOrder` 不是 `sort`、`RecommendResult.isExposed` 是 tinyint 0/1 不是布尔。所以：

- 需求评审时引用的必须是本仓库的文件路径 + 行号，不接受「前端那边是这么写的」。
- 后端改名/删字段就是**破坏性变更**，必须显式说明影响哪几端哪些调用点，由用户安排同步节奏。
- 反过来，前端提出的「字段对不上」要以本仓库现状为裁决依据 —— 先打开 controller 看真实签名，再决定是前端改还是后端补。

### 权威的物理位置

| 契约要素 | 唯一定义处 |
| --- | --- |
| URL 前缀与转发关系 | `vidora-gateway/src/main/resources/application.yml` 的 `routes`（Path 断言 + `StripPrefix=1`） |
| 方法、路径、参数名、返回类型 | 各服务 `{service}/controller/*Controller.java` |
| 请求体字段与校验文案 | `{service}/dto/XxxRequest.java`（jakarta validation 注解 + message） |
| 响应字段与可空性 | entity（`{service}/entity/`；RBAC 那几张 `sys_*` 在 `vidora-modules/vidora-system/.../system/entity/`）+ `vo/` |
| 外壳与错误码 | `common-core/.../ApiResult.java`、`ResultCode.java`、`GlobalExceptionHandler.java` |
| 谁能调 | `GatewayAuthFilter`（匿名白名单 + `ADMIN_PATH_PREFIXES`）+ `@PreAuthorize` + `SQL/vidora_cloud.sql` 第 1 节的 `sys_menu.permission_code` 种子 |
| 字段取值语义 | `SQL/vidora_cloud.sql` 的列注释（枚举档位以此为准） |

包根固定 `src/main/java/org/tiglor/{service}/`。

### 已知不可依赖的部分

- `common-core/.../PageResult.java`：**零引用**，实际分页出口是 MP 的 `Page<T>`。给前端讲契约时报 `Page` 的形状。
- `ARCHITECTURE.md` 技术栈表写 MyBatis-Plus 3.5.14，根 `pom.xml` 是 3.5.17。以 pom 为准。
- 匿名可达清单会随需求变动，**用到时现场读 `GatewayAuthFilter.java`**，不要引用本文档或历史对话里的列表。注意判定顺序：匿名浏览白名单最先判（命中即放行、不解析 token），`ADMIN_PATH_PREFIXES` 的 clientKey 校验在 token 校验之后 —— 同一个路径可能有相反命运（`/api/search/suggests` 同时出现在两张名单上：游客放行、带普通用户 token 反而 403）。

---

## 五、什么算完成

一个需求只有同时满足下列条件才算 done，才可以对用户说「完成」：

1. **代码落在正确的层与服务**：Controller 只做绑定与包装，规则在 service/impl，SQL 在 mapper，公共物在 common-*；没有把别的域的写口揽进来。
2. **契约经现场核对**：涉及的每个字段都能指出它在哪个 Controller / DTO / entity 的哪一行。凭印象的一律不算。
3. **四处同步检查过**（若接口是管理端专用）：网关路由、`ADMIN_PATH_PREFIXES`、`sys_menu` 权限种子、`@PreAuthorize`。少一处都是事故，逐条对照 `.code/skills/add-admin-only-endpoint.md`。
4. **异常语义正确**：业务失败抛 `BizException` + 合适的 `ResultCode`，HTTP 状态码不等于 200；不存在的情况返回 404 而不是 null。
5. **数据变更落在 `SQL/vidora_cloud.sql` 所属业务那一节**：改那条 `CREATE TABLE` 的目标态（不新建迁移文件、不在里面追加 `ALTER`、建库建表不带 `IF NOT EXISTS`），种子数据用 `INSERT IGNORE` 且写死 id，并给出存量库的等价 `ALTER` 或重建步骤（`DROP DATABASE vidora_cloud;` 后重跑）；**明确告知用户需手工执行**（AI 默认不代跑）。
6. **旁路设施没被绕过**：需要的地方加了 `@OperLog`（并按 `BusinessType` 选对类型）；不该加的地方（高频用户流水、机器回报口）确认没加；缓存写入侧配了 `@CacheEvict`。
7. **编译通过**：第三节命令形式的离线编译，退出码 0；改了 common-* 用 `-am` 或全仓编。
8. **运行时行为有证据或如实标缺**：起服务 + curl 走过正常/未登录/越权/幂等四条路径的，贴结果；没走的，写「未验证 + 阻塞原因」。
9. **交付说明区分已验证/未验证**，规则见 `agent-rules.md` 第六节。本仓库测试基础设施不完整，**不许声称「测试通过」**；也不许声称跑过 lint / 格式检查（不存在）。
10. **行尾保持原样**，diff 里没有整文件重写；无新增散落日志文件（`.gitignore` 已覆盖 `**/*.log`、`**/logs/`）。
11. 若过程中发现规范与代码不一致，**回来修订 `.code/` 对应文件**（修订前征求用户同意）。
