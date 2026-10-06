# Skill：验证一次后端改动

适用：任何改动交付前的最后一步。「代码看起来对」不是验证，**只有下面这些命令真跑过并贴出结果，才可以写进交付说明**。

前置阅读：`.code/agent-rules.md` 第三节（命令原文与为什么每个参数不能少）、第六节（交付口径）。

---

## 第 1 层：编译（AI 可独立完成，必做）

```bash
export JAVA_HOME="D:/Java/otherJDK/bellsoft-jdk25.0.4.1+1-windows-amd64/jdk-25.0.4.1"

# A. 只改了某个服务
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile

# B. 改了网关 / 公共模块 —— 把受影响下游一起带上
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-gateway -am compile
mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-common/common-log -am compile

# C. 全仓（最稳，慢一些）
mvn -o -Dmaven.legacyLocalRepo=true compile
```

实测记录：A、C 均 `BUILD SUCCESS`（退出码 0）。判断标准是**退出码 + 有无 `[ERROR]`**，不是「有没有输出」。

| 现象 | 含义 | 处理 |
| --- | --- | --- |
| `WARNING: ... jansi ... / sun.misc.Unsafe::objectFieldOffset ...` | Maven 自身跑在新 JDK 上的正常噪音 | 忽略 |
| `-q` 下无任何输出 | 成功（quiet 模式只报错误） | 检查 `echo $?` = 0 |
| `Could not resolve dependencies` / 联网超时 | 漏了 `-o` 或漏了 `-Dmaven.legacyLocalRepo=true` | 补上参数重跑 |
| `invalid target release: 25` / `Unsupported class file major version` | 没设 `JAVA_HOME`（默认 java 是 1.8） | 先 export |
| `找不到符号 IService / ServiceImpl` | 导成了 `extension.service.*`，应为 **`com.baomidou.mybatisplus.spring.service[.impl]`** | 改 import |
| 找不到 `spring-boot-starter-aop` | Boot 4 已改名 **`spring-boot-starter-aspectj`** | 见 `common-log/pom.xml` |
| Jackson 类冲突/选型异常 | Boot 4 容器内是 Jackson 3（`tools.jackson`），本工程统一 **Jackson 2（`com.fasterxml`）** | 对齐现有 import |

**编译绿的边界**：它只能证明类型对得上。路由缺失、权限漏配、SQL 未执行、字段语义错、运行时 N+1 —— 全都编得过。**不许把「编译通过」写成「功能已验证」**。

引了 Dubbo 的模块还要多一层警惕：服务导出发生在启动那一刻，编译期完全看不出问题。已踩到两例（`ARCHITECTURE.md` §0 的 #7 #8）—— `mysql-connector-j` 带的 protobuf 4.x 顶掉 Dubbo 要的 3.x，以及缺 gson 时 `JsonUtils` 的 SPI 探测从 `hasNext()` 抛出 `NoClassDefFoundError`。**新增 Dubbo provider/consumer 后，编译绿不等于能起来**，这类改动要在交付说明里写明「未经启动验证」。

## 第 2 层：静态一致性走查（AI 必做，不需要环境）

按改动类型逐条 grep，每条都要看到预期命中：

```bash
cd E:/Project/vidora/vidora-cloud

# 新接口有没有路由（没有就是 404）
grep -n "Path=" vidora-gateway/src/main/resources/application.yml

# 管理端口有没有进 ADMIN_PATH_PREFIXES（没有就是越权）
grep -n '"/api/' vidora-gateway/src/main/java/org/tiglor/gateway/filter/GatewayAuthFilter.java

# 权限码：Controller 用的和 SQL 种子给的是否同一个字符串
grep -rn "hasAuthority(" --include="*.java" vidora-modules/{目标服务}
grep -n "permission_code\|'{资源}:{动作}'" SQL/vidora_cloud.sql

# 审计：注解在不在、title 是否与同模块一致
grep -rn "@OperLog" --include="*Controller.java" vidora-modules/{目标服务}

# entity 字段是否真能在 DDL 里找到对应列
grep -n "{列名}" SQL/vidora_cloud.sql

# 接口说明与字段描述：改了 controller／DTO／实体就要验注释有没有烘出来。
# 编译通过对「没写注释」完全无感，文档只是静默少描述，所以这一步不能省（机制与两个 Windows 坑见 coding-standards 3.1）。
python .code/tools/check-endpoint-javadoc.py            # 形状：第一行接口名、不许单行紧凑式、不许贴签名
python .code/tools/check-endpoint-javadoc.py --baked    # 同上，但读烘出的 JSON＝springdoc 真正当成 summary 的那一行
ls vidora-modules/{目标服务}/target/classes/org/tiglor/{域}/{dto,entity}/*__Javadoc.json | head
mvn -o dependency:build-classpath -Dmdep.outputFile=/c/tmp/cp/{目标服务}.txt -pl vidora-modules/{目标服务}
"$JAVA_HOME/bin/javac.exe" -encoding UTF-8 -cp "$(tr -d '\r\n' < /c/tmp/cp/{目标服务}.txt)" \
  -d /c/tmp .code/tools/FieldProbe.java
# 公共模块的编译产物排在前面，否则继承来的 BaseEntity 字段读的是旧 jar，虚报缺描述
"$JAVA_HOME/bin/java.exe" -Dstdout.encoding=UTF-8 \
  -cp "C:/tmp;vidora-common/common-core/target/classes;vidora-common/common-log/target/classes;vidora-modules/{目标服务}/target/classes;$(tr -d '\r\n' < /c/tmp/cp/{目标服务}.txt)" \
  FieldProbe {全限定类名...}      # 输出「缺=0」才算字段描述齐

# 契约下游：三端的类型文件（admin src/api/types.ts、mobile src/types/index.ts、web src/api/*.js 的注释）是各自手抄的副本，
# 后端改了出入参形状不会让它们编译报错 —— 这里没有可跑的生成步骤，只有两条人工动作：
#   1) 服务起着时核对文档描述是否真带出来了：GET http://127.0.0.1:{服务端口}/v3/api-docs 里看目标类的 properties
#   2) 交付里点名「改了哪个字段 / 哪一端的哪个类型文件要跟着改」（判据见 coding-standards 3.1）

# 缓存名是否已登记
grep -n "{域}:" vidora-common/common-redis/src/main/java/org/tiglor/common/redis/CacheNames.java

# 行尾有没有被改坏 —— 用字节计数。别用 `grep -cU $'\r'`：本机 Git Bash 下它把每一行都判成命中，
# 对纯 LF 文件也报「有 CR」，是个假阳性判据（实测 RemoteLogSink.java 报 113，字节数其实是 0）。
for f in {改过的文件}; do printf "%s: " "$f"; cr=$(tr -dc '\r' < "$f" | wc -c); lf=$(tr -dc '\n' < "$f" | wc -c); [ "$cr" = 0 ] && echo "LF" || { [ "$cr" = "$lf" ] && echo "CRLF" || echo "MIXED(坏了)"; }; done
git diff --stat        # 期望：新增行数 ≈ 真实改动量；整文件重写说明格式化过头了
```

**验证点判据**：`git diff --stat` 里若某个既有文件显示为「全部行都改了一遍」，几乎一定是行尾/缩进被动过 —— 回滚该文件重来（本仓库行尾逐文件混用，见 `.code/agent-rules.md` 第四节）。

## 第 3 层：运行时验证（需用户同意起服务）

启动服务、连库属于用户的运行环境。征得同意后再做。

### 3.1 起最小集合

调试业务接口通常需要：MySQL + Redis + Nacos + gateway + auth-service + 目标服务。三种起法由用户选（IDEA 运行各 `{X}ServiceApplication`、`mvn spring-boot:run`、或 `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d`，见 `deploy/docker-compose.yml` 头部步骤）。

依赖没起时的表现要知道，别误判成 bug：**Nacos 未就绪时服务仍能启动，只是注册不上、网关找不到下游**（compose 注释原文）→ 症状是 503/连接失败而不是 404。

### 3.2 拿 token

登录需要 `clientId`（三端各一个固定值，见 `SQL/vidora_cloud.sql` 第 1 节的 `sys_client` 种子）：

| client_key | client_id | 过期 |
| --- | --- | --- |
| web | `vidora-web-2024` | 7 天 |
| mobile | `vidora-mobile-2024` | 30 天 |
| admin | `vidora-admin-2024` | 8 小时 |

```bash
# 账号即手机号；口令向用户索取，不要猜、不要写进任何提交文件
ADMIN_TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"<管理员手机号>","password":"<口令>","clientId":"vidora-admin-2024"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

USER_TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"phone":"<普通用户手机号>","password":"<口令>","clientId":"vidora-web-2024"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
```

`LoginVO` 里的字段名以 `vidora-auth/src/main/java/org/tiglor/auth/vo/LoginVO.java` 为准 —— 上面的提取式子如果抓不到东西，先去读那个类，别改 curl 蒙。

### 3.3 五类用例，逐条记录实际状态码

```bash
BASE=http://127.0.0.1:8080/api

# ① 正常路径（必须带 -i 看状态码和 X-Trace-Id）
curl -si -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/categories/page?current=1&size=5"

# ② 参数非法 → 期望 400 + ApiResult.code=400 + 中文校验文案
curl -si -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":""}' "$BASE/categories"

# ③ 资源不存在 → 期望 404，绝不是 200+data:null
curl -si -X PUT -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"x"}' "$BASE/categories/99999999"

# ④ 未登录 → 期望 401 且【响应体为空】（GatewayAuthFilter.unauthorized 只设状态码就 setComplete）
curl -si "$BASE/categories/page"

# ⑤ 越权（管理端接口专用，这一条最能暴露 ADMIN_PATH_PREFIXES 漏配）
curl -si -H "Authorization: Bearer $USER_TOKEN" "$BASE/categories/page"   # 期望 403
```

判读要点：

- **HTTP 状态码必须与 body 里的 `code` 一致**（`GlobalExceptionHandler` 的设计前提）。出现 200 + `code:400` 说明有人在 Controller 里手动返回了 error 外壳 —— 这是回归。
- 每条响应头里的 `X-Trace-Id` 记下来，用于第 4 层对日志。
- 匿名白名单判定**先于** token 解析：某个 GET 不带 token 能通，不代表它安全；而同一路径带普通用户 token 可能反而 403（`/api/search/suggests` 就在两张名单上）。所以**不要用前缀推断某接口能不能调**，现场读 `GatewayAuthFilter.java`。
- 分页契约核对：`data` 应是 `{records,total,size,current,pages}`（MP 的 `Page`），不是 `PageResult`。

### 3.4 绕开网关直连服务（定位问题归属时用）

```bash
curl -si -H "X-User-Id: 1" -H "X-User-Permissions: content:category:manage" \
  -H "X-Client-Key: admin" http://127.0.0.1:8103/categories/page
```

`HeaderAuthenticationFilter` 就是靠这些头还原身份（常量在 `common-core/.../security/SecurityHeaders.java`）。直连通、经网关不通 → 问题在路由或 StripPrefix；两边都不通 → 问题在服务本身。

注意：**这只用于诊断**。手工伪造身份头不等于验过了真实鉴权链路，第 ⑤ 条那种真 token 的用例仍然必须跑。

## 第 4 层：日志与审计验证（改了日志/审计链路时必做）

```bash
# 目录名是 spring.application.name（如 content-service），不是目录名 vidora-content
tail -n 20 logs/content-service/app.log
grep "<刚才记下的 traceId>" logs/content-service/app.log logs/system-service/app.log
grep "X-Trace-Id\|<traceId>" logs/gateway-service/app.log || true   # 网关侧预期命中不到：它不写 MDC
tail -n 5  logs/content-service/audit.log      # 一行一条 JSON，kind=oper
tail -n 5  logs/system-service/audit.log       # system-service 侧还会落库
```

判据：

- 一个 traceId 能把各**下游服务**的日志串起来（`TraceIds.MDC_KEY`）。注意 `logs/gateway-service/app.log` 里的 `traceId=` **是空的**：网关 pom 里没有 `vidora-common-log` 依赖（只有 `logback-spring.xml` include 了 common-core 那份 XML），所以既没有 `TraceIdFilter`，MDC 也没有值 —— 这是设计如此，不是故障（`TraceIdGlobalFilter` 注释说明了 WebFlux 下刻意不用 MDC）。跨服务关联请从下游日志或响应头 `X-Trace-Id` 取值。
- `audit.log` 有、库里没有 → 上报链路问题（system-service 没起 / Nacos 没解析 / `sys_oper_log` 表未建 / `X-Ingest-Token` 不符），不是切面问题。
- 库里两条都有、`audit.log` 没有 → 有人改坏了 sink 顺序（设计上是先文件后上报，`RemoteLogSink.submit`）。
- 敏感字段应显示 `"***"`。故意传一个含 `password` 的入参验一次。

同时确认没有把日志拉回仓库根：`git status` 不应出现新的 `*.log`（`.gitignore` 覆盖 `**/*.log` 与 `**/logs/`，历史散文件已在 `logs/legacy/`）。

## 第 5 层：数据变更后的自检（迁移由用户执行完才做）

```bash
DESCRIBE sys_user;                                  # 新列在不在、默认值对不对
SELECT COUNT(*) FROM sys_menu WHERE id BETWEEN 60 AND 62;   # 种子插了几条
SELECT role_id, menu_id FROM sys_role_menu WHERE menu_id >= 60;
```

AI 不代跑迁移，但可以（在用户允许只读查询的前提下）跑 SELECT 核对结果。**UPDATE/DELETE/DDL 一律不碰。**

---

## 交付说明怎么写

分两块，逐条对齐真实跑过的命令：

```markdown
## 已验证
- `mvn -o -q -Dmaven.legacyLocalRepo=true -pl vidora-modules/vidora-content -am compile` 退出码 0，无 [ERROR]。
- 静态走查：新前缀 /api/xxxs/ 已出现在 GatewayAuthFilter.ADMIN_PATH_PREFIXES 第 NN 行；
  权限码 xxx:list 在 Controller 与 SQL/vidora_cloud.sql 的菜单种子里字面一致。
- （若真调过）curl GET /api/xxxs/page with admin token → 200，data.records 3 条；
  with web token → 403 {"code":403,"message":"该接口仅限管理端访问"}。

## 未验证
- 服务未启动（等用户批准），第 3 层 5 类用例本次全部未跑。
- SQL/vidora_cloud.sql 的新增段落未执行（按约定由用户手工执行），因此菜单种子与权限生效与否未知。
- audit.log / sys_oper_log 的落库链路本次未对照。
- 三端类型未同步：本次改了出入参形状，而 admin `src/api/types.ts` / mobile `src/types/index.ts` / web `src/api/*.js` 的注释都是手抄副本，后端编译通过不代表它们已对齐（见 coding-standards 3.1）。
```

硬性禁令（重申）：

- 不写「测试通过」—— 测试基础设施不完整（只有 3 个模块有 10 个测试文件，且 surefire 版本坑会让 JUnit5 静默跳过）。要跑就先跑再贴摘要，否则不提。
- 不写「lint / 格式检查通过」—— 仓库里没有 checkstyle / spotless 配置。
- 不写「迁移已执行 / 数据库已更新」—— AI 无权执行。
- 不写「前端也验过了」—— 除非真在同级仓库打开了对应调用点核对。
- 阻塞就说清阻塞在哪（服务没起 / 缺账号 / 缺口令 / 库没建），不要含糊过去。
