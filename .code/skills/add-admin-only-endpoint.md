# Skill：新增一个「仅管理端可用」的接口

适用：接口只应被 `vidora-admin` 调用，普通用户（web / mobile）拿到 token 也不能访问。

这是本仓库**最容易出事**的一类改动：安全边界由两处独立配置共同构成，漏掉第二处时接口编译通过、路由通、服务正常，只是任何登录用户都能读后台数据 —— **没有任何报错会提醒你**。

前置阅读：`.code/agent-rules.md` 第四节（禁止动作）、`.code/coding-standards.md` 第 3 节、`ARCHITECTURE.md` 的鉴权链路章节。

---

## 四处必须同步，缺一不可

| # | 位置 | 作用 | 漏掉的后果 |
| --- | --- | --- | --- |
| 1 | `vidora-gateway/src/main/resources/application.yml` → `spring.cloud.gateway.server.webflux.routes` | 让请求能到达服务 | 404（会被立刻发现，代价最小） |
| 2 | `vidora-gateway/src/main/java/org/tiglor/gateway/filter/GatewayAuthFilter.java` → `ADMIN_PATH_PREFIXES` | 非 admin clientKey 一律 403 | **静默越权**：普通用户 token 可直接访问 |
| 3 | `SQL/vidora_cloud.sql` 第 1 节 → `sys_menu` 权限种子 + `sys_role_menu` 授权 | 让角色真的拿到权限码 | 管理员调用永远 403 |
| 4 | Controller 方法上的 `@PreAuthorize("hasAuthority('...')")` | 服务内接口级授权，粒度到方法 | 整个 Controller 对所有登录用户开放 |

第 2 与第 4 是**两层不同粒度**：第 2 层按路径前缀拦客户端类型（粗，全有或全无），第 4 层按权限码拦具体操作（细）。同一资源里「读给用户、写给管理」就要靠路径切分 + 权限码配合，见下面的陷阱 3。

---

## 步骤

### 1. 定路径：让它能被前缀匹配

网关判的是 `path.startsWith(prefix)`（`GatewayAuthFilter.isAdminPath`），所以**前缀必须以 `/` 收尾**才能锁住整棵子树。现有条目就是这个形状：`"/api/users/"`、`"/api/clients/"`、`"/api/oper-logs/"`。

命名用复数 kebab-case，与既有风格一致（`/api/oper-logs`、`/api/login-logs`、`/api/feed-configs`）。

### 2. 加网关路由

在目标服务已有的 route 上追加 Path，或新开一条：

```yaml
            - id: system-log-service
              uri: lb://system-service
              predicates:
                # 操作日志 / 登录日志查询，只有 read 接口（没有删除与清空）。
                # 「仅限管理端」由 GatewayAuthFilter 的 ADMIN_PATH_PREFIXES 判定，不靠路由
                - Path=/api/oper-logs/**,/api/login-logs/**
              filters:
                - StripPrefix=1
```

要点：`id` 全局唯一；`uri` 用 `lb://{spring.application.name}`；`StripPrefix=1` 剥掉 `/api`，所以 Controller 上写 `/oper-logs`。

**验证点**：改完看缩进是否与相邻列表项一致；该文件实测行尾为 LF，别让工具写成 CRLF。

### 3. 加 ADMIN_PATH_PREFIXES

```java
    /** 仅限管理端（clientKey=admin）访问的接口前缀 */
    private static final Set<String> ADMIN_PATH_PREFIXES = Set.of(
            "/api/users/",
            // ...
            // 审计日志：记录里带着入参、返回值和客户端 IP，普通用户的 token 不该读到这些
            "/api/oper-logs/",
            "/api/login-logs/"
    );
```

每个新前缀一行，并留一句「为什么这个要锁」（照抄上面注释的语气）。`Set.of` 重复元素会抛异常，加完编译一次就能确认没撞车。

**同时检查另外三张名单会不会误放行它**（`GatewayAuthFilter` 顶部）：`PUBLIC_GET_PATHS`（精确）、`PUBLIC_GET_PREFIXES`（前缀）、`VIDEO_DETAIL_PATH`（正则）。匿名白名单**判定在前且命中即放行、不解析 token** —— 如果你的新路径恰好落进某个公开前缀，第 2 层的 403 根本不会执行。例：想锁 `/api/search/suggests/admin/`，而 `/api/search/suggests` 已在 `PUBLIC_GET_PATHS` 上，就得逐条核对匹配关系。

### 4. 权限码 + sys_menu 种子

权限码格式 `{域}:{资源}:{动作}`，实际用词：`:list` / `:add` / `:edit` / `:delete` / `:manage` / `:assign` / `:audit` / `:purge` / `:view`。字典类维护常用一个 `:manage` 覆盖全部写操作（`content:category:manage`）；不可逆清理要单独一档（`recommend:manage` vs `recommend:purge`）。

在 `SQL/vidora_cloud.sql` 第 1 节（用户与权限）尾部追加一段 `INSERT IGNORE`（不新建文件；写法照该节里 33–37 与 46–48 那几段，编号规则见 `.code/skills/add-a-db-migration.md`）：

```sql
-- 管理端菜单：Xxx 管理（挂在"系统管理"目录 id=8 下）
-- id 从 NN 起接已有最大 id；icon 是 Element Plus 图标组件名，写错不报错、只会静默渲染成空白
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `icon`, `sort_order`, `permission_code`, `visible`) VALUES
(60, 8,  'Xxx管理',   2, '/system/xxx', 'pages/system/xxx', 'Setting', 6, NULL,           1),
(61, 60, 'Xxx查询',   3, NULL,          NULL,               NULL,      1, 'xxx:list',     0),
(62, 60, 'Xxx删除',   3, NULL,          NULL,               NULL,      2, 'xxx:delete',   0);

INSERT IGNORE INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (1,60),(1,61),(1,62);
```

字段语义（现场读 `SQL/vidora_cloud.sql` 第 1 节的 `sys_menu` 建表段确认）：

| 列 | 取值 |
| --- | --- |
| `menu_type` | 1-目录 2-菜单 3-按钮 |
| `path` / `component` | 页面级（type=2）填前端路由与组件路径；按钮级（type=3）填 NULL |
| `visible` | 目录/页面 1；纯权限位按钮 0（不进侧边栏） |
| `permission_code` | **挂在按钮上，页面菜单本身通常留 NULL**（与客户端管理 33–37、日志审计 49–53 一致） |
| `icon` | Element Plus 图标组件名，PascalCase；管理端 `<component :is="icon"/>` 渲染 |

先查当前最大 id，别撞上：

```bash
grep -hoE "^\(([0-9]+)," SQL/vidora_cloud.sql | tr -d '(,' | sort -n | tail -3
```

**迁移由用户手工执行 —— AI 不得连库跑。** 交付时要写明「菜单种子尚未入库，因此权限未生效」。

### 5. Controller 上标 @PreAuthorize

```java
@GetMapping("/page")
@PreAuthorize("hasAuthority('xxx:list')")
public ApiResult<Page<Xxx>> page(...) { return ApiResult.ok(service.page(...)); }
```

启动类链路上的前提（已就位，改动时别破坏）：各服务 `config/SecurityConfig.java` 上有 `@EnableWebSecurity @EnableMethodSecurity`，`HeaderAuthenticationFilter` 把网关注入的 `X-User-Roles` / `X-User-Permissions`（常量在 `common-core/.../security/SecurityHeaders.java`）还原成 GrantedAuthority。`AccessDeniedException` 已由 `GlobalExceptionHandler` 映射成 403，不要自己 catch。

注意根 pom 的前提：maven-compiler-plugin 必须 `<parameters>true</parameters>`，否则 SpEL 参数名解析失败。

### 6. （可选）补审计

如果这是删除/授权/审核/改配置类写操作，转 `.code/skills/add-an-audited-write.md`。

### 7. 编译

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-gateway -am compile
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-{service} -am compile
```

### 8. 鉴权边界验证（这一步不能省，也证明不了「AI 自己做完了」）

需要用户同意启动 gateway + 目标服务，然后用**两种 token** 各打一遍。拿 token 的方式见 `.code/skills/verify-a-backend-change.md`（admin 用 `sys_client` 里 `client_key='admin'` 那条的 `client_id`，普通用户用 `vidora-web-2024`）。

| 用例 | 期望 |
| --- | --- |
| 不带 token | 401（**响应体为空** —— `unauthorized()` 只设状态码就 `setComplete()`） |
| web/mobile token | 403 + body `{"code":403,"message":"该接口仅限管理端访问"}` |
| admin token，无权限码 | 403（Spring Security 那层，body 是 `ApiResult`） |
| admin token，有权限码 | 200 + `data` |

第 2 条返回 200 = **`ADMIN_PATH_PREFIXES` 漏配**，这是本次改动唯一的严重缺陷，必须修完重测。

---

## 常见坑

1. **只加路由不加前缀名单**。最常见也最危险，症状是「一切正常」。测试时必须拿**普通用户 token** 试，光用 admin token 验不出这个洞。
2. **前缀少写结尾斜杠**。`"/api/users"` 也能 startsWith 匹配到 `/api/usersomething`，多锁或不锁；反之有人为了保险写更宽前缀会误锁别的资源。参考 `/api/comments/admin/` 那条的注释：`/api/comments/**` 整体是用户端共用的，只能锁后台子路径。
3. **把整段共用前缀拉黑**。同一资源既有用户口又有管理口时（评论：发/看归用户，`/{id}/audit` 归管理），要么拆出 `/admin/` 子路径，要么完全交给 `@PreAuthorize` 的权限码，别在路径前缀上一刀切。
4. **以为 permitAll 就是暴露**。`SecurityConfig` 里的 `/internal/**` 不是公网面：网关只路由 `/api/**`，从不转发 `/internal`（`InternalLogController` 与 system 的 `SecurityConfig` 注释都写了这条，以及残留风险 —— Nacos discovery locator 理论上可达，所以那里额外加了 `X-Ingest-Token` 校验）。**不要因为看到 `permitAll` 就去收紧它**，那会断掉所有服务的审计上报。同理 `/auth/**`、swagger、actuator 是刻意放开的。
5. **忘了 `sys_role_menu` 授权**。菜单种子插了但没给角色 1 关联，管理员照样 403。两段 INSERT 都要写。
6. **`menu_type` 与 `permission_code` 挂错层**。历史上 22–26 曾被建成「按钮却当页面用」，后来补了 38–45 的页面级菜单才修好。新加时明确：可导航的是 type=2 带 path/component，纯权限位是 type=3 且 visible=0。
7. **改了 yml 没重启网关**。路由与 `ADMIN_PATH_PREFIXES` 都在启动时加载，热改无效。重启属于用户环境操作，要先问。
8. **动别人的名单**。`PUBLIC_GET_PATHS` / `ADMIN_PATH_PREFIXES` 的任何增删都是安全口径变更，需用户批准（`.code/agent-rules.md` 第五节第 1 条）。
9. **以为前端隐藏按钮等于安全**。三端的按钮可见性只是体验；唯一防线是这里的第 2 层和第 5 步。
