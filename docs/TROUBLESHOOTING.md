# RedoReplicator 故障排查

排查时先停止进程并保留 `data/`、`output/`、`logs/` 和当前 YAML。不要手工修改
H2 `runtime_state`，也不要删除 JSONL 来“对齐”位点。

## 启动前检查失败

先运行：

```bash
bin/validate.sh
```

常见原因：

- YAML 权限不是 `0600`：修正权限，避免明文密码被其他用户读取。
- Oracle 账号无权读取 `SYS.V_$*` 或字典表：重新执行对应账号 SQL，再运行
  `sql/validate_capture_user.sql`。
- 多 PDB 模式无法 `SET CONTAINER`：使用手动创建的公共账号，并将 JDBC 连接到
  `CDB$ROOT`。
- redo 路径未映射：以 Oracle 查询返回的绝对路径为准，增加最长前缀映射，并在
  RedoReplicator 进程内确认文件可读。
- 数据库不是 ARCHIVELOG 或 supplemental logging 不足：手动执行
  `sql/configure_database.sql`。

## 找不到 redo/archive 或 sequence gap

`10039`、`10044` 或 “missing sequence” 表示需要的文件不存在、暂不可读，或
online redo 已覆盖。不要把位点跳到更晚 sequence。处理顺序：

1. 用 `bin/status.sh` 记录当前安全 SCN、thread、sequence 和文件。
2. 在 Oracle 查询对应 archive 是否仍存在。
3. 检查 FRA/oradata 是否挂载到 Sidecar，并确认只读权限。
4. 恢复缺失 archive 后重新启动；程序会从 H2 安全位点继续。

如果文件已永久丢失，当前任务无法无损继续，必须从仍有完整 redo 链的更早备份或
新任务开始，不能静默跳过。

## redo header、checksum 或截断错误

- `40003`：文件短于 header/块边界，或读取后文件缩短。
- `40008`/`40009`：DBID、RESETLOGS、thread 或 SCN 与 JDBC 目录不一致。
- `60024`：文件或块 sequence 不匹配。
- `60025`：checksum 不匹配；online 正在写入时会有限重试，超过上限后停止。

确认路径映射没有指向同名旧文件，并核对当前 incarnation。不要关闭校验来绕过
身份、sequence 或 checksum 错误。

## 表结构无法证明

`50071` 表示目标 SCN 的完整结构无法从 H2、Oracle Flashback 或 redo 重建。
程序会停止而不是输出无结构或错误列值。检查：

- 目标 SCN 对应的 SYS 字典 redo/archive 是否仍在。
- 捕获账号是否有 SYS 字典表的 SELECT 与 FLASHBACK 权限。
- include 正则是否包含正确的 PDB、owner 和 table。
- DDL 事务是否完整，是否缺失 CREATE/ALTER/DROP 相关 redo。

启动时尚未创建的匹配表可以为空；后续 CREATE 必须同时具备用户和表空间引用字典
以及完整 DDL redo。

## JSONL 与 H2 不一致

- JSONL 大于 H2 安全偏移：重启会截断未提交尾部，再重放，可能重复完整事务。
- JSONL 小于 H2 安全偏移：程序停止。恢复被删除的 JSONL 或使用一致的备份，不能
  手工向前改 H2 位点。
- 文件尾部没有 commit：消费者必须丢弃该事务，等待重放后的完整事务。

## H2 迁移、备份和恢复

H2 升级只允许向前迁移。迁移前会在 `data/backups/` 创建备份；失败后保留原库。

```bash
bin/stop.sh
bin/backup.sh
bin/restore.sh data/backups/<backup>.zip
bin/validate.sh
bin/start.sh
```

restore 会校验包内 SHA-256、DBID、incarnation 和 RESETLOGS。旧文件保留在
`restore-safety-*` 目录。

## rewind 被拒绝

`rewind.sh` 只允许目标 SCN 小于或等于当前安全 SCN，并要求：

- Oracle 身份与 H2 一致。
- 存在覆盖目标 SCN 到当前时间的连续 redo/archive 链。
- 目标 SCN 的所选表结构可以完整证明。

成功 rewind 后旧 H2 保留在 `rewind-safety-*`，旧 JSONL 不删除，新输出使用更大
文件编号。

## 长事务 spill

`data/tmp/transaction-*.spill` 只属于未提交事务。正常 commit/rollback 后会删除；
崩溃重启时会清理旧 spill，并从 low-watermark 重放。不要在进程运行时手工删除。

复测 100,000 行门禁：

```bash
mvn -q -Dredoreplicator.test.scale=true \
  -Dtest=RedoTransactionBufferScaleTest test
```

## 收集诊断信息

提交问题前保留：

- RedoReplicator 版本与 `SHA256SUMS`。
- Oracle 完整版本、CDB/PDB 名称、DBID、incarnation、RESETLOGS。
- `data/status.json`、错误前后的主日志和 console.log。
- 错误中的 redo 文件、thread、sequence、offset、SCN 和阶段。
- 已脱敏的 YAML 路径映射与 include/exclude 规则。

不要提交数据库密码、完整业务 JSONL 或未脱敏的数据文件。
