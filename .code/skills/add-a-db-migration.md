# Skill：改数据库结构（SQL/vidora_cloud.sql，单库单文件按业务分节）

适用：建新表、加列、改列、补种子数据、修数据。

**前置铁律：`SQL/vidora_cloud.sql` 默认由用户手工执行。** AI 不得自行连库跑 DDL/DML，不得用 `mysql` 客户端，也不得通过任何 ORM 入口间接执行。只有用户在当前会话里点名要你跑，才可以跑，且跑完必须把执行前后的库状态（`SHOW TABLES` / 行数 / `DESCRIBE`）都贴出来。理由见第三节。

前置阅读：`.code/agent-rules.md` 第四节、`SQL/vidora_cloud.sql` 的「二、审计日志」节头注释（为什么审计表不给删除口、为什么昵称不落冗余列 —— 本仓库归属决策写得最完整的一处）。

---

## 一、目录形态：一个库、一个文件，文件内按业务功能分节

`SQL/` 下只有一个文件 `vidora_cloud.sql`：库名 `vidora_cloud`，33 张表分 8 节（2026-10-06 实测）。

| 节 | 表归属服务 | 表 |
| --- | --- | --- |
| 一、用户与权限 | system-service | sys_user / sys_role / sys_menu / sys_user_role / sys_role_menu / sys_client / user_tag / user_follow / user_oauth_bind + 全部 RBAC 菜单与客户端种子（auth-service 不连库，经 Dubbo 向 system-service 取） |
| 二、审计日志 | 写入方是全部业务服务，查询在 system-service | sys_oper_log / sys_login_log + 日志菜单种子（id 49–53） |
| 三、视频与转码 | video-service | video_info / video_transcode_task / video_multipart_upload / video_audit_record |
| 四、内容运营 | content-service | content_category / content_tag / content_feed_config / content_hot_search / content_security_audit + 一级分区种子 |
| 五、互动 | interact-service | interact_action / interact_comment / interact_danmaku / interact_play_count |
| 六、站内消息 | message-service | message_record / message_conversation / message_push_device |
| 七、搜索 | search-service | search_history / search_keyword_stat / search_suggest |
| 八、推荐 | recommend-service | recommend_result / recommend_algo_config / recommend_user_feature |

**全文件恰好一条 `CREATE DATABASE` + 一条 `USE`，都是 `vidora_cloud`；建库建表一律普通 DDL，不带 `IF NOT EXISTS`**（为什么见第 3 小节）。交付前用命令核对，不要凭本表格：

```bash
grep -a -e '^CREATE DATABASE' -e '^USE ' SQL/vidora_cloud.sql   # 各一条（行首锚定，别把头部注释数进来）
grep -ac '^CREATE TABLE' SQL/vidora_cloud.sql                  # 表总数 = 33
grep -ac 'COMMENT=' SQL/vidora_cloud.sql                       # 表级注释，应与表数相等
grep -ac "COMMENT '" SQL/vidora_cloud.sql                      # 列级注释，当前 309（每列一条）
grep -an '^-- [一二三四五六七八]、' SQL/vidora_cloud.sql        # 节名与节序 = 8
grep -an 'IF NOT EXISTS' SQL/vidora_cloud.sql                  # 只应命中头部那条解释性注释
```

### 为什么是一个库

原先是「一个微服务一个 schema」（`user_service`…`recommend_service` 七个库、七个文件），由用户决定收成主库 `vidora_cloud`。开源里这也是常见形态（RuoYi、litemall 都是单库单 SQL 脚本，内部按模块分节）。

**库名是 `vidora_cloud`，不是 `vidora`**，也别改回 `vidora`：`vidora` 是产品名，当库名用就把 `vidora_{域}` 这套命名空间占了，将来按业务拆库只能另起前缀（`vidora_biz` 也否过 —— `biz` 说明不了库里装了什么）。`cloud` 与仓库名对齐，指「后端这一包共用的主库」。合库是过渡态，拆的映射已经留好 —— 一节搬一个库（`vidora_user` / `vidora_video` / `vidora_content` / `vidora_interact` / `vidora_message` / `vidora_search` / `vidora_recommend`，审计要独立就再立 `vidora_log`），表名前缀即归属，改动只有 `CREATE DATABASE` 那两行和 7 个 datasource URL。

**代价必须知道：库级隔离没有了。** 七个服务连的是同一个库，任何一节的表都能被别的服务 `SELECT` 到。边界改由两条软约束守：

1. **表名前缀就是归属**（`sys_`/`user_` 归 system、`video_` 归 video……），一个服务的 Mapper 只碰自己那一节的表；
2. **要别人的数据就走服务间调用**（HTTP 契约在 `vidora-api`，RPC 走 Dubbo），不写跨节 join、不直连别人的表。

推论：**仍然不建外键**。同库之后 MySQL 技术上支持外键了，但跨服务外键会把服务边界变成部署耦合（拆库、改名、按租户分库都要先解锁约束），引用完整性照旧在 service 层守，并在 entity 注释里写明这个限制（`Category` 类注释是范本）。

### 不保留迁移脚本

`08_` 起曾经放过 `NN_migration_{主题}.sql`（给存量库打补丁），**这个约定已作废**。作废的原因是它必然漂移：迁移文件与基线里的 DDL 逐字重复、各改各的，几周后没人判断得出哪份是准的。现在只有一个来源 —— 目标态。

推论三条：

1. **改结构就改对应节里那条 `CREATE TABLE`**，直接改那一行的定义或注释，不在文件里追加 `ALTER`。文件必须始终等于「新环境应该长成的样子」。
2. **存量库的差异由用户处理**（重建，或手工执行等价 DDL）。工程里没有 Flyway / Liquibase，没人替你算这一步，所以交付里必须写出这次变更在已建好的库上等价于哪几条 `ALTER`（见第 5 步）。
3. **新表不另开文件、不新开库**。它进所属业务那一节；一节装不下了再谈分节，而不是分文件。

### 归属判定：业务功能决定节，服务所有权决定谁能写

审计表被全部业务服务写入、表结构类在 common-log，但查询要联 `sys_user` 取昵称，所以它单列一节、写入口只有 system-service 一个。合库之后「落在哪个库」不再是归属问题，归属问题变成「哪个服务有权写这张表」—— 看表前缀和它的 Mapper 在哪个模块。

行尾：`SQL/vidora_cloud.sql` 是 **LF**（合库时新建的文件），改它请保持 LF。判据用字节数 —— `grep -cU $'\r'` 在本机 Git Bash 下会把每一行都判成命中，别用它：

```bash
printf "CR=%s LF=%s\n" "$(tr -dc '\r' < SQL/vidora_cloud.sql | wc -c)" "$(tr -dc '\n' < SQL/vidora_cloud.sql | wc -c)"
```

CR=0 是 LF，CR=LF 是 CRLF，其余是 MIXED（说明编辑器把文件改坏了，回滚重来）。

---

## 二、步骤

### 1. 定位到对应节，改目标态

| 变更类型 | 落在哪 |
| --- | --- |
| 加一张新表 | 所属业务那节末尾追加一段 `CREATE TABLE`（不带 `IF NOT EXISTS`），表注释与列注释写全 |
| 加列 / 改列 / 改注释 | 直接改那张表的 `CREATE TABLE` 里那一行 |
| 补种子数据 | 追加一段 `INSERT IGNORE`（菜单 33–53 就是这样一段段追加上去的，不重构已有段落） |
| 修数据 / 回填 | 目标态里没有「修复」这回事 —— 把结果形态写进建表与种子；确实要改存量数据的，输出 SQL 交用户执行，不进 `SQL/` |

### 2. 描述信息：表注释与列注释都必须有

**建表时就要写全，不是事后补。** 规则两条，没有例外：

1. 每张表 `) ENGINE=InnoDB ... COMMENT='这张表是什么';`
2. 每一列（含 `id` / `create_time` / `update_time` / `is_deleted` 这四个「看起来不用解释」的）都要 `COMMENT '...'`。枚举列的注释要把每个档位写出来（`business_type` 那样：`'业务类型：0-其他 1-新增 …'`），因为 Java 侧的枚举常量和三端的下拉文案都以它为第二处出处。

为什么这条要写进规范：`sys_client` 建表时四个基础列漏了 COMMENT，在库里放了很久没人发现 —— 管理端看表结构、新人看库、`SHOW FULL COLUMNS` 全是空白，只能回去翻代码猜。

自检（改完 SQL 跑一遍，第一段输出应为空，第二段两个数字应相等）：

```bash
# 漏列注释的行
grep -nE "^[[:space:]]*\`[a-z_]+\`[[:space:]]+[A-Za-z]" SQL/vidora_cloud.sql | grep -v COMMENT
# 建表数与带表级 COMMENT 的行数
printf "tables=%s  带表注释=%s\n" "$(grep -c '^CREATE TABLE' SQL/vidora_cloud.sql)" "$(grep -cE "^\) ENGINE=InnoDB .*COMMENT='" SQL/vidora_cloud.sql)"
```

### 3. 写法清单：建库建表用普通 DDL，只有种子数据讲幂等

| 目的 | 写法 | 备注 |
| --- | --- | --- |
| 建库 | `CREATE DATABASE \`vidora_cloud\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` | 全文件一条，紧跟着一条 `USE \`vidora_cloud\`;` |
| 建表 | `CREATE TABLE \`t\` (...) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='...'` | 不带 `IF NOT EXISTS`，原因见下；字符集与排序规则必须逐字对齐，`utf8mb4_unicode_ci` 才让重名判断天然大小写不敏感（`content_category` 的 `uk_parent_name` 注释解释了这件事）；末尾的 `COMMENT=` 是强制项，见第 2 步 |
| 种子数据 | `INSERT IGNORE INTO \`t\` (\`id\`, ...) VALUES (...)` | **必须显式列出列名 + 写死 id**，见下 |
| 授权关系 | `INSERT IGNORE INTO \`sys_role_menu\` (\`role_id\`, \`menu_id\`) VALUES (1,60),(1,61);` | 依赖表上的 `UNIQUE KEY uk_role_menu` 才幂等 |

**DDL 为什么写普通形态**。`IF NOT EXISTS` 的语义是「已存在就静默跳过、整条语句当成功」，于是你在 `CREATE TABLE` 里改的列定义对一个存量库一个字都没生效，而执行的人看到「没报错」以为生效了 —— 这个错觉是本仓库明确不要的行为。普通 DDL 会直接炸：`ERROR 1050 Table 'xxx' already exists`，当场告诉执行者「这是已有库，请走重建或手工 ALTER」。所以 `vidora_cloud.sql` 的语义是**全量建库脚本**，不是可重跑的补丁；重建路径就是 `DROP DATABASE vidora_cloud;` 后重跑本文件。

**那 DDL 改动怎么落到已有库**：两条路，重建（开发库首选）或手工执行等价 `ALTER`（见第 5 步）。`INSERT IGNORE` 保留是为了让「重导入某一节种子」「补齐漏掉的菜单行」不炸在已有行上 —— 那是 DML 的幂等，别把它当成 DDL 也可以重跑的理由，两者不是一回事。

**种子数据必须写死主键 id**，不能靠 AUTO_INCREMENT。理由写在分区种子的注释里：`vidora-web` 首页/上传页/顶栏至今按 id 1-7 引用分区，一旦某行被删过再靠自增生成就会整体错位，点「音乐」出来的是「游戏」的内容。同理 `sys_menu` 的 id 是授权关系的锚点。

查当前最大 id（避免撞车）：

```bash
grep -hoE "^\(([0-9]+)," SQL/vidora_cloud.sql | tr -d '(,' | sort -n | tail -3
```

### 4. 同步 Java 侧

DDL 落地后必须一起改，否则接口读写会炸：

| DDL 变更 | Java 侧 |
| --- | --- |
| 新表 | `{service}/entity/Xxx.java` + `mapper/XxxMapper.java`；有 `is_deleted` 才 `extends BaseEntity` |
| 加列 | entity 加同名字段（camelCase，靠 `map-underscore-to-camel-case`）；若要暴露给前端再加进 DTO/VO |
| 枚举档位变化 | entity 字段注释 + DDL 列注释 **两处都改**，还要通知三端 |
| 唯一索引 | service 层的预检查要用 SQL 等值比较而不是 Java 比较，语义才对得上排序规则（`CategoryServiceImpl.requireNameAvailable` 注释） |

逻辑删除列的名字与取值固定在 yml：`logic-delete-field: isDeleted`、`logic-not-delete-value: 0`、`logic-delete-value: 1`（各服务 `application.yml`）。新表想启用逻辑删除就照这套命名，别自创。

改了表名前缀或挪动归属时，同步检查 7 个 `vidora-modules/*/src/main/resources/application.yml` 里的 datasource URL 和它上面那句归属注释还说不说得通（现在 7 份都指向 `jdbc:mysql://127.0.0.1:3306/vidora_cloud`，注释写明本服务管哪一节的表）。

### 5. 交付但不执行

做完之后向用户输出：

- 改了哪一节、动了哪几张表（`git diff --numstat -- SQL/`）。
- **存量库的等价 DDL**。例如「`sys_user` 加一列」→ `ALTER TABLE vidora_cloud.sys_user ADD COLUMN foo TINYINT NOT NULL DEFAULT 0 COMMENT '...';`，并注明「本会话未执行」。开发库通常直接重建更省事（`DROP DATABASE vidora_cloud;` 后重跑 `SQL/vidora_cloud.sql`），把两个选项都给出来。
- 执行前需要确认的点：是否有同名列、种子 id 是否与现库冲突、能否重建。
- 执行后的自检 SQL（`SHOW TABLES` 数一数量 / `DESCRIBE` 看列）。

**不要**在交付里写「数据库已更新」「迁移已生效」—— 默认你无权也没做这件事。

---

## 三、为什么改完 SQL 不会自动生效（三个层次都要知道）

1. **工程里没有自动化工具**：根 pom 与各模块 pom 都没有 Flyway / Liquibase，代码里也没有启动时执行脚本的逻辑。SQL 文件是给人读的、给人跑的。
2. **docker-compose 挂载只在「首次初始化」有效**：`deploy/docker-compose.yml` 把 `../SQL` 挂到 `/docker-entrypoint-initdb.d:ro`，MySQL 官方镜像的行为是**仅当数据目录为空时**按文件名顺序执行目录下全部文件。`mysql-data` 卷已经存在的话，脚本一次都不会再跑 —— 改了 `vidora_cloud.sql` 再 `docker compose up` 起来的服务连的还是老库，要重建得先 `docker compose down -v`（删卷，卷里的数据一起没，动手前确认没有要留的东西）再 up，这正是普通 DDL 假设的重建路径。
3. **本地开发环境根本不走容器**：各服务 `application.yml` 默认连 `127.0.0.1:3306`，库是你手工建的。

推论：**代码可以先于 DDL 合并**。如果你的 entity 已经引用了新列而库里没有，接口会在运行时炸（Unknown column），编译期完全看不出来。这类改动要在交付里明确标为「未验证 —— 等待 DDL 执行」。

---

## 常见坑

1. **自己去连库执行**。默认禁止（`.code/agent-rules.md` 第四节）。尤其注意：用户说「帮我看看库里有没有这张表」也不等于授权你改数据；只读 SELECT 也要先问。
2. **往文件里追加 `ALTER`**。文件就不再是目标态了，下一个人无从判断哪条是最终形态。改结构就改 `CREATE TABLE` 里那一行。
3. **以为重跑 `vidora_cloud.sql` 就把已有库更新到新形态**。普通 DDL 在第二条语句上就报错停下（`ERROR 1007 Can't create database` / `ERROR 1050 Table already exists`），库一个字没变。只能重建（`DROP DATABASE vidora_cloud;` 后重跑）或按交付里给出的等价 `ALTER` 手工执行。
4. **种子数据不写 id**。AUTO_INCREMENT 受插入顺序和删除历史影响，而三端可能正按 id 引用这些行。
5. **`INSERT INTO` 不带 IGNORE**。第二次执行就是主键冲突中断整个脚本。
6. **手滑加了第二个库**。合库之后全文件只允许一条 `CREATE DATABASE` + 一条 `USE`，且都是 `vidora_cloud`；用第一节那条命令核对。
7. **排序规则换掉**。默认一律 `utf8mb4 / utf8mb4_unicode_ci`（compose 里 MySQL 启动参数也是这个）。换成 `_bin` 或 `_cs` 会让「同一父分类下重名」的判断行为改变。
8. **给审计表配删除口**。设计禁区，见 `.code/skills/add-an-audited-write.md` 坑 7；`SQL/vidora_cloud.sql` 审计节头注释就是这条决策的记录。
9. **给跨节表建外键**。同库了，MySQL 建得上，但那是把服务边界焊死成部署耦合。引用完整性照旧在 service 层守（`Category` 类注释是范本）。
10. **拿单库当许可去 join 别人的表**。别的服务的表能被 `SELECT` 到不代表可以直读：列改名、拆库、加租户前缀都会让它当场炸。跨域取数据走 `vidora-api` / Dubbo。
11. **NULL 参与唯一索引**。MySQL 唯一索引把 NULL 当作互不相同的值，可空列进唯一键等于约束失效。`content_category` 的做法是把 `parent_id` 设成 `NOT NULL DEFAULT 0`，Java 侧用常量 `Category.ROOT_PARENT_ID = 0L`。
12. **「未复核」用 0 表示**。`content_security_audit` 里 `manual_result` 刻意用 NULL 表示未复核，注释说明 0 在 TINYINT 里既不是放行也不是拦截，会被误读成第三种状态。新表的三态字段照此办理。
13. **顺手格式化整个 SQL 文件**。`vidora_cloud.sql` 有 800 行，全文重写后 diff 不可读。只改需要改的那几行。
14. **重构已有段落**。追加新段落可以；改已定型的建表段会影响所有新环境，先问用户。
15. **给单张表另开一个文件**。历史上出现过按表命名的 `09_sys_log.sql` 和按库命名的 `01`–`07`，都已并回这一个文件。
16. **手滑把 `IF NOT EXISTS` 加回建库建表**。看着更「稳」，实际是把上面那条报错换成静默跳过：结构改动对存量库不生效，而执行的人看到没报错以为生效了 —— 这正是本仓库废弃它的原因。幂等只留给种子数据的 `INSERT IGNORE`。
