# Skill：给一个写操作补审计记录

适用：某个管理动作需要「出了事能追责到人」—— 删除、授权、审核、改配置、状态流转。

前置阅读：`.code/coding-standards.md` 第 8 节（日志）、`vidora-common/common-log/src/main/java/org/tiglor/common/log/annotation/OperLog.java` 的类注释（判定标准原文在那里）。

---

## 一、先判断该不该打（这一步比怎么写更重要）

判据不是「是不是写操作」，而是**「出了事要不要追责到人」**。

| 该打 | 不该打 | 现有例子 |
| --- | --- | --- |
| 删除、批量清理 | 用户自己的内容流水 | `@OperLog(title="分类管理", type=BusinessType.DELETE)` vs 发评论/点赞/弹幕/播放计数 |
| 授权变更 | 高频读 | `("角色管理", GRANT)`、`("菜单管理", GRANT)` |
| 人工审核改判 | 机器回报口（见下） | `("内容审核", AUDIT)`、`("评论管理", AUDIT)` |
| 运营位与配置改动 | 登录（走 `sys_login_log`，不走 oper） | `("信息流配置", UPDATE)`；`AuthController.login` 用 `LoginAuditor` |
| 上下架 / 状态流转 | 注册（是账号创建不是认证事件） | `("分类管理", CHANGE_STATUS)`；`AuthController.register` 无审计 |

理由写在 `OperLog.java` 与 `AuthController` 的注释里：业务流水和审计的量级差两个数量级，混进同一张表会把真正要查的淹掉。

**机器回报口一律不打 `@OperLog`**。范本是 `SecurityAuditController`：同一个 Controller 里 `POST /machine`（内容服务机审跑完后回报）**没有**注解，只有 `PUT /{id}/review`（人工复核）有。原因是回报由服务自动触发、可能重试、每条内容都会来一次 —— 它是数据管道而不是「人的决定」。加进去之后审计表里全是机器人刷屏，真出事时查不到那一条人工放行。同理 `VideoController.upload` 也不打（用户上传行为），而 `POST /{id}/transcode`（管理员触发的转码）打了。

登录链路是另一条路：`vidora-auth/src/main/java/org/tiglor/auth/support/LoginAuditor.java` 手写 `LoginLogEntity` 直接 `logSink.submit(...)`，成功与失败都记（失败必须记 —— 爆破、撞库的现场只在失败记录里）。**不要给 `/auth/login` 加 `@OperLog`**。

---

## 二、步骤

### 1. 选 BusinessType

`vidora-common/common-log/src/main/java/org/tiglor/common/log/annotation/BusinessType.java`，落库存 int，管理端按值翻译文案。**只列这个项目里真实存在的动作**，不要照抄 RuoYi 的全量枚举，也别新增没人用的值（多出来的值只会变成查询条件里的死选项）。

| 枚举 | code | 用在 |
| --- | --- | --- |
| `OTHER` | 0 | 不好归类的写操作，如刷新缓存 |
| `INSERT` / `UPDATE` / `DELETE` | 1 / 2 / 3 | 常规增删改 |
| `GRANT` | 4 | 改角色菜单、改用户角色 |
| `EXPORT` / `IMPORT` | 5 / 6 | 导出导入 |
| `CLEAN` | 7 | 批量清空一整类数据，风险高于普通 DELETE |
| `AUDIT` | 8 | 内容审核：过审 / 驳回 |
| `CHANGE_STATUS` | 9 | 上下架、状态流转 |

DDL 里的 `business_type` 列注释（`SQL/09_sys_log.sql`）就是这串数字的第二处出处，**两处必须一致**；真要扩新值属于改公共契约，先问用户。

### 2. 加注解

```java
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
```

三个参数的讲究：

- **`title` 是模块名**，管理端按它筛选（`OperLogController.page` 的 `title` 参数）。同一模块的所有接口用同一个字符串，别一会儿「分类管理」一会儿「视频分类」。现有取值：`分类管理` `标签管理` `热搜管理` `信息流配置` `内容审核` `评论管理` `视频管理` `用户管理` `角色管理` `菜单管理` `客户端管理` `算法配置`。
- **`saveParam` 默认 true**。入参里有明文凭据时必须置 false —— 登录、改密这类。审计表是给人查的，不该变成密码明文仓库。
- **`saveResult` 默认 false**。列表接口的返回值能把审计表撑爆；只在确实要留证据出口时开。

脱敏是自动的，但要知道它的边界：`common-log/.../sink/AuditJson.java` 按**键名子串**匹配 `password|passwd|secret|token`（大小写不敏感）把值替换成 `"***"`，值侧兼容字符串与标量。**这是按名字猜，不是白名单字段表** —— 所以新增一个叫 `apiKey` / `privateKey` / `credential` 的凭据字段不会被脱敏，那种接口必须自己 `saveParam = false`。（按名匹配的理由写在注释里：新增 `pushToken`、`clientSecret` 时没人会想起来改审计模块的清单，漏一次的后果是明文口令永久躺在审计表里。）

### 3. 确认这个服务已经接了 common-log

```bash
grep -n "vidora-common-log" vidora-modules/vidora-{service}/pom.xml
```

已有：除 `vidora-gateway` 外的全部可运行服务（gateway 是 WebFlux，`CommonLogAutoConfiguration` 上标了 `@ConditionalOnWebApplication(type = SERVLET)`，它只用 jar 里的 logback 定义）。缺了就要加依赖 —— **属于 pom 变更，先问用户**。

### 4. 确认切面生效的前提

| 前提 | 位置 | 断了会怎样 |
| --- | --- | --- |
| AspectJ starter | `common-log/pom.xml` 用的是 **`spring-boot-starter-aspectj`**（Boot 4 改名，老的 `starter-aop` 不存在） | 注解静默无效，无报错 |
| 自动配置被加载 | `common-log/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 切面 Bean 不存在 |
| 方法是 public 且经代理调用 | `OperLogAspect` 是 `@Around("@annotation(operLog)")` | 同类内部自调用不走代理 → 不记 |
| 请求上下文存在 | `RequestContextHolder` 取 request（拿不到就只记方法不记 URL/IP） | MQ 消费者、定时任务里打注解会得到残缺记录 |
| `vidora.log.oper-enabled` 未被关闭 | `CommonLogAutoConfiguration`（matchIfMissing = true，默认开） | 整条链路无声关闭 |

### 5. 理解它落到哪儿（三条路同时成立）

`OperLogAspect.around` 的 finally 里调 `sink.submit(record)`，具体 Bean 由 `CommonLogAutoConfiguration` 按 `vidora.log.ingest-enabled` 二选一，且都带 `@ConditionalOnMissingBean(LogSink.class)`：

| 场景 | 生效 Bean | 行为 |
| --- | --- | --- |
| `ingest-enabled=true`（默认） | `RemoteLogSink` | **先**写一份 `logs/<app>/audit.log`（`FileLogSink` 是它的内部依赖，不是并列 Bean），**再**丢线程池异步 POST `http://system-service/internal/logs/{oper,login}`（服务名由 Nacos 解析，走 `LoadBalancerInterceptor`；带可选 `X-Ingest-Token`；绕开网关鉴权） |
| `ingest-enabled=false` | `FileLogSink` | 只写 `audit.log` |
| system-service 自己 | `vidora-modules/vidora-system/.../sink/DatabaseLogSink.java` | `@Component` 实现了 `LogSink`，让上面两个自动退避；先写文件再落库 |

两条硬约束（`OperLogAspect` 类注释）：业务异常原样抛出不改变语义；取现场数据全过程包 try/catch，审计自己出问题只留一行 warn，不能把用户的删除请求变成 500。上报队列打满用 `CallerRunsPolicy`（宁慢勿丢），失败退回文件。

**这些都不需要你写代码**，你要做的只是第 2 步。但排查「为什么没记录」时必须按这张表倒着查。

### 6. 编译

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-{service} -am compile
```

### 7. 验证（需用户同意起服务）

三处都要看到同一条：

```bash
# 1) 本地兜底文件一定先有（服务没起 system-service 也应有这一行）
tail -n 3 logs/vidora-{service}/audit.log        # 目录名是 spring.application.name，如 content-service
# 期望：一行 JSON，kind=oper，data.title / businessType / operUserId / traceId 都有值

# 2) 库里可查（迁移已执行 + admin token）
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "http://127.0.0.1:8080/api/oper-logs/page?title=分类管理&current=1&size=5"

# 3) 详情里能看到大字段
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "http://127.0.0.1:8080/api/oper-logs/{id}"
```

顺带验脱敏：故意传一个含 `password` 字段的入参，`oper_param` 里应是 `"***"` 而不是原文。

---

## 常见坑

1. **给机器回报口打注解**。`SecurityAuditController.reportMachine` 故意没有 `@OperLog` —— 那是数据管道不是人的决定。加了以后审计表全是机器人，真要查「谁放行了这条违规内容」时反而找不到。
2. **给登录加 `@OperLog`**。登录走 `sys_login_log`（`LoginAuditor`，成败都记），不是 `sys_oper_log`。两套表、两条语义。
3. **`title` 随意写**。管理端按 title 精确筛选，写成第二个变体就等于新建了一个筛选项。
4. **入参含凭据却忘了 `saveParam=false`**。脱敏只认 `password/passwd/secret/token` 四个词根，`apiKey`、`credential`、`otpCode` 不在内。
5. **同类内部自调用**导致切面不生效（和 `@Cacheable` 同一个坑）。注解打在 Controller 方法上最稳 —— 现有 29 处全在 controller。
6. **以为上报失败就没记录**。`RemoteLogSink` 先落文件后上报，`audit.log` 是唯一那条一定成功的路（合规要的是「发生过什么」）。反过来，看到 audit.log 有、库里没有，说明是上报链路问题（服务没起 / Nacos 没解析 / 表未建 / 口令不符），不是切面问题。
7. **想给审计表加删除或清空接口**。**设计上的禁区**：`OperLogController` 只有 page 与 detail 两个 GET，`SQL/09` 头部注释写明「能被随手清空的审计表等于没有审计表，『谁删了视频』和『谁删了那条记录』必须是两件事」，保留期靠归档/运维解决。有人提这个需求要先挡回去并交给用户。
8. **列表接口去查两个大字段**。`oper_param` / `json_result` 各 VARCHAR(2000)，列表刻意不查（`OperLogController.detail` 的注释：「列表为了不把几千字的请求参数整页拖过来，故意不查大字段」）。优化查询时别把它「补全」。
9. **昵称冗余进表**。`sys_oper_log` 只存 `oper_user_id`，昵称由查询侧在同库批量补（`OperLogVO extends OperLogEntity` + 其类注释解释了改名后冗余值会永远停在旧名字）。
10. **审计逻辑里抛异常**。任何取现场数据的代码都要包在旁路 try/catch 内，绝不能让审计把业务打成 500。
11. **`sys_oper_log.status` 方向搞反**：1-成功 0-失败（`record.setStatus(error == null ? 1 : 0)`）。
