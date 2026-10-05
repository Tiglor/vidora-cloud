# Skill：新增一次数据库变更（SQL 迁移）

适用：建新表、加列、补种子数据、修数据。

**前置铁律：`SQL/*.sql` 由用户手工执行。** AI 不得连库跑迁移，不得用 `mysql` 客户端、不得通过任何 ORM 入口间接执行 DDL。理由见第三节。

前置阅读：`.code/agent-rules.md` 第四节、`SQL/09_sys_log.sql` 的头部注释（为什么单独成文件、为什么不给删除口，是本仓库迁移文档写得最完整的一份）。

---

## 一、现状与编号规则

目录 `E:\Project\vidora\vidora-cloud\SQL\`：

| 文件 | 库 | 内容 |
| --- | --- | --- |
| `01_user_service.sql` | `user_service` | sys_user / sys_role / sys_menu / sys_user_role / sys_role_menu / sys_client / user_tag + **全部 RBAC 菜单种子**；auth-service 与 system-service **共用**这个库 |
| `02_video_service.sql` | `video_service` | video_info / transcode_task 等 |
| `03_content_service.sql` | `content_service` | content_category / content_tag / content_feed_config / content_hot_search / content_security_audit + 一级分区种子 |
| `04_interact_service.sql` | `interact_service` | 评论 / 互动动作 / 弹幕 / 播放计数 |
| `05_message_service.sql` | `message_service` | 消息记录 / 会话 / 推送设备 |
| `06_search_service.sql` | `search_service` | 搜索历史 / 词频 / 建议词 |
| `07_recommend_service.sql` | `recommend_service` | 推荐结果 / 算法配置 / 用户特征 |
| `08_migration_user_theme_key.sql` | `user_service` | **纯迁移脚本范例**：给存量库补 `sys_user.theme_key` |
| `09_sys_log.sql` | `user_service` | sys_oper_log / sys_login_log + 日志审计菜单种子 |

编号约定（从现状归纳）：

- `01`–`07` = **一个服务一个 schema**，各自 `CREATE DATABASE IF NOT EXISTS` + `USE` + 全量建表 + 初始数据。这是「新环境一键建库」的基线文件。
- `08`、`09` 起是**增量**：`NN_{主题}.sql`，不建库（只 `USE` 已有库），只做一件事，文件名要说清做什么（`08_migration_user_theme_key.sql`）。
- 下一个可用编号：**10**。先 `ls SQL/` 确认，别凭本文档。
- 跨库的公共表按「写入方是谁」归置：审计表虽被所有业务服务写，但表结构属于 common-log、查询要联 `sys_user`，所以放 `user_service` 并独立成 `09`（该文件头部注释就是这个决策的记录）。

行尾提醒：`SQL/01_user_service.sql` 实测 CRLF，`SQL/09_sys_log.sql` 与 `SQL/08_*.sql` 实测 LF。**新建文件跟最近的那份走（LF）**，改既有文件保留它原来的行尾。

---

## 二、步骤

### 1. 选对文件

| 变更类型 | 落在哪 |
| --- | --- |
| 给某个域加一张**新表**（且是新环境的组成部分） | 追加进对应的 `0X_{service}_service.sql`，同时考虑是否需要一份对应 `NN_` 迁移给存量库 |
| 给存量表**加列/改列** | 新建 `NN_migration_{表}_{列}.sql`，并在对应 `0X` 文件的 CREATE TABLE 里同步加上这一列（否则新环境缺列） |
| 补**种子数据**（菜单权限位、字典初始项） | 优先追加式 INSERT 到新 `NN_` 文件（历史上 33–48 就是这样补进 `01` 之后的段落）；改语义则新开文件 |
| 数据修复 / 回填 | 独立 `NN_` 文件，带明确的 WHERE 边界和影响行数说明 |

**两处都要改**是本类任务最容易漏的点：只改迁移脚本，新环境建出来的表缺列；只改建表脚本，存量库永远缺列。`08` + `01` 就是一对：`01` 的 CREATE TABLE 已含 `theme_key`，`08` 的文件头注释直说「本脚本只服务于库早就建好了的环境」。

### 2. 幂等写法清单

MySQL 没有 `ADD COLUMN IF NOT EXISTS`，所以要靠「可重复执行 + 报错可忽略」的组合：

| 目的 | 写法 | 备注 |
| --- | --- | --- |
| 建库 | `CREATE DATABASE IF NOT EXISTS \`x\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;` | |
| 建表 | `CREATE TABLE IF NOT EXISTS \`t\` (...) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='...'` | 字符集与排序规则必须逐字对齐，`utf8mb4_unicode_ci` 才让重名判断天然大小写不敏感（`03` 的 `uk_parent_name` 注释解释了这件事） |
| 加列 | `ALTER TABLE \`t\` ADD COLUMN ... AFTER \`col\`;` | 重复执行报 `1060 Duplicate column name` —— **属预期，在文件注释里写明让人忽略**（`08` 就是这么写的） |
| 种子数据 | `INSERT IGNORE INTO \`t\` (\`id\`, ...) VALUES (...)` | **必须显式列出列名 + 写死 id**，见下 |
| 授权关系 | `INSERT IGNORE INTO \`sys_role_menu\` (\`role_id\`, \`menu_id\`) VALUES (1,60),(1,61);` | 依赖表上的 `UNIQUE KEY uk_role_menu` 才幂等 |
| 回填 | `UPDATE ... WHERE col IS NULL;` | 写成可重跑的形态，带 LIMIT 或明确条件 |

**种子数据必须写死主键 id**，不能靠 AUTO_INCREMENT。理由写在 `03` 的分区间释里：`vidora-web` 首页/上传页/顶栏至今按 id 1-7 引用分区，一旦某行被删过再靠自增生成就会整体错位，点「音乐」出来的是「游戏」的内容。同理 `sys_menu` 的 id 是授权关系的锚点。

查当前最大 id（避免撞车）：

```bash
grep -hoE "^\(([0-9]+)," SQL/*.sql | tr -d '(,' | sort -n | tail -3
```

### 3. 写文件头注释

每个迁移文件开头讲清三件事（照 `08` / `09` 的格式，用 `-- ====` 分隔线）：

1. 这次变更是什么、为什么（业务动机，不是「加个字段」）。
2. 为什么放在这个库里（涉及归属判断时）。
3. **重复执行的后果**（哪些报错是预期的、可以忽略）和有没有需要人工确认的前置条件。

### 4. 同步 Java 侧

DDL 落地后必须一起改，否则接口读写会炸：

| DDL 变更 | Java 侧 |
| --- | --- |
| 新表 | `{service}/entity/Xxx.java` + `mapper/XxxMapper.java`；有 `is_deleted` 才 `extends BaseEntity` |
| 加列 | entity 加同名字段（camelCase，靠 `map-underscore-to-camel-case`）；若要暴露给前端再加进 DTO/VO |
| 枚举档位变化 | entity 字段注释 + DDL 列注释 **两处都改**，还要通知三端 |
| 唯一索引 | service 层的预检查要用 SQL 等值比较而不是 Java 比较，语义才对得上排序规则（`CategoryServiceImpl.requireNameAvailable` 注释） |

逻辑删除列的名字与取值固定在 yml：`logic-delete-field: isDeleted`、`logic-not-delete-value: 0`、`logic-delete-value: 1`（各服务 `application.yml`）。新表想启用逻辑删除就照这套命名，别自创。

### 5. 交付但不执行

做完之后向用户输出：

- 改了哪些 SQL 文件（路径 + 新增行数）。
- **待用户执行的命令形式**（例如 `mysql -u root -p < SQL/10_xxx.sql`），并注明「本会话未执行」。
- 执行前需要确认的点：目标库是否已存在、是否有同名列、种子 id 是否与现库冲突。
- 执行后的自检 SQL（SELECT 数一下行数 / DESCRIBE 看列）。

**不要**在交付里写「数据库已更新」「迁移已生效」——你无权也没做这件事。

---

## 三、为什么迁移不会自动生效（三个层次都要知道）

1. **工程里没有自动化工具**：根 pom 与各模块 pom 都没有 Flyway / Liquibase，代码里也没有启动时执行脚本的逻辑。SQL 文件是给人读的、给人跑的。
2. **docker-compose 挂载只在「首次初始化」有效**：`deploy/docker-compose.yml` 把 `../SQL` 挂到 `/docker-entrypoint-initdb.d:ro`，MySQL 官方镜像的行为是**仅当数据目录为空时**按文件名顺序执行。`mysql-data` 卷已经存在的话，一个脚本都不会再跑。（顺带一提：该文件顶部注释写「SQL/ 下 7 个脚本」，实际目录已有 9 个 —— 注释过期，以文件为准。）
3. **本地开发环境根本不走容器**：各服务 `application.yml` 默认连 `127.0.0.1:3306`，库是你手工建的。

推论：**代码可以先于迁移合并**。如果你的 entity 已经引用了新列而迁移还没执行，接口会在运行时炸（Unknown column），编译期完全看不出来。这类改动要在交付里明确标为「未验证 —— 等待迁移执行」。

---

## 常见坑

1. **自己去连库执行**。禁止动作（`.code/agent-rules.md` 第四节）。尤其注意：用户说「帮我看看库里有没有这张表」也不等于授权你改数据；只读 SELECT 也要先问。
2. **只改迁移不改建表脚本**（或反之）。新环境与存量环境从此不一致，而且这种漂移通常在几周后才被发现。
3. **种子数据不写 id**。AUTO_INCREMENT 受插入顺序和删除历史影响，而三端可能正按 id 引用这些行。
4. **`INSERT INTO` 不带 IGNORE**。第二次执行就是主键冲突中断整个脚本。
5. **忘了 `USE \`库名\``**。脚本跑到错误的库里，或者依赖调用方的默认库。
6. **排序规则换掉**。默认一律 `utf8mb4 / utf8mb4_unicode_ci`（compose 里 MySQL 启动参数也是这个）。换成 `_bin` 或 `_cs` 会让「同一父分类下重名」的判断行为改变。
7. **给审计表配删除口**。设计禁区，见 `.code/skills/add-an-audited-write.md` 坑 7；`09` 的注释就是这条决策的记录。
8. **跨库建外键**。7 个 schema 是 7 个库，MySQL 不支持跨库外键。引用完整性只能在 service 层守，并在 entity 注释里写明这个限制（`Category` 类注释是范本）。
9. **NULL 参与唯一索引**。MySQL 唯一索引把 NULL 当作互不相同的值，可空列进唯一键等于约束失效。`03` 的做法是把 `parent_id` 设成 `NOT NULL DEFAULT 0`，Java 侧用常量 `Category.ROOT_PARENT_ID = 0L`。
10. **「未复核」用 0 表示**。`03` 里 `manual_result` 刻意用 NULL 表示未复核，注释说明 0 在 TINYINT 里既不是放行也不是拦截，会被误读成第三种状态。新表的三态字段照此办理。
11. **顺手格式化整个 SQL 文件**。行尾混用，且大文件（`01` 有 18KB）全文重写后 diff 不可读。
12. **改编号已定的历史脚本的既有段落**。追加新段落可以；重构 `01`–`07` 的建表段会影响所有新环境，先问用户。
