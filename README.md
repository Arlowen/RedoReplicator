# RedoReplicator

[English](README_EN.md) | [实施计划](IMPLEMENTATION_PLAN.md) | [故障排查](docs/TROUBLESHOOTING.md)

RedoReplicator 是 OpenLogReplicator 的 JDK 17 纯 Java 翻译版本，直接读取
Oracle online redo 和 archive 文件，不使用 LogMiner、JNI、OCI、`Unsafe` 或
C/C++ 动态库。项目当前版本是 `0.1.0-SNAPSHOT`，尚未发布 `0.1.0`，也不会提前
发布 `1.0.0`。

## 当前能力

- 支持 Oracle 19c 和 Oracle AI Database 26ai Free。
- 支持单实例非 CDB、单 PDB，以及同一 CDB 下多个 `READ WRITE` PDB。
- 使用 JDBC 查询数据库身份、redo 目录和字典；redo 字节始终从本地只读文件系统读取。
- 支持 INSERT、UPDATE、DELETE、DDL、完整回滚和 rollback to savepoint。
- 支持交错事务、提交顺序输出、跨文件长事务和磁盘 spill。
- 支持分区表、隐藏列、row migration/chaining、rowdependencies。
- 支持 BASICFILE BLOB/CLOB、以 CLOB 存储的 XMLType，以及上游全部 138 个字符集注册。
- 固定输出 OpenLogReplicator 原生 JSONL，先 `fsync` JSONL，再提交 H2 安全位点。
- H2 保存唯一安全位点和表结构历史；支持 backup、restore 和 SCN rewind。
- 故障恢复允许重复完整事务，但不允许跳过已提交事务。

明确不支持 RAC、ASM、Data Guard、远程 redo 文件系统、TDE、Heap/HCC
compression、NOLOGGING、二进制 XMLType、Kafka、网络输出和离线 Batch 模式。

## 部署拓扑

RedoReplicator 必须部署在 Oracle 主机上，或作为共享同一 Oracle 数据卷的
Sidecar。应用账号只通过 JDBC 连接数据库；容器或主机还必须把 Oracle 返回的
redo/archive 路径映射为进程可读的本地路径。

```text
Oracle JDBC ──身份、字典、文件目录──┐
                                   ├─ RedoReplicator ─ JSONL
本地 redo/archive 只读文件系统 ─────┘                  └ H2
```

应用不会自动创建 Oracle 账号，也不会修改账号权限。账号与数据库配置由用户手动执行。

## 解压后启动

运行包已携带非 Temurin 的 OpenJDK 17 jlink runtime，目标主机不需要预装 Java。

```bash
tar -xzf redo-replicator-0.1.0-SNAPSHOT-linux-arm64.tar.gz
cd redo-replicator-0.1.0-SNAPSHOT
```

先阅读并手动执行：

- `sql/configure_database.sql`：启用归档和必要的 supplemental logging。
- `sql/create_common_capture_user.sql`：推荐的 CDB root / 多 PDB 公共账号。
- `sql/create_capture_user.sql`：直接连接单个 PDB 时使用的本地账号。
- `sql/validate_capture_user.sql`：验证账号权限。

编辑 `conf/redo-replicator.yaml`，至少填写 JDBC、账号、表范围和 redo 路径映射。
密码允许明文保存在 YAML 中，因此配置文件权限应为 `0600`。

```yaml
database:
  url: jdbc:oracle:thin:@//127.0.0.1:1521/FREE
  username: C##REDO_CAPTURE
  password: change_me
  redoPathMappings:
    - oracle: /opt/oracle/oradata
      local: /oracle/oradata

capture:
  startScn:
  includeTables:
    - FREEPDB1\.APP\..*
  excludeTables: []

output:
  directory: output
  maxFileSizeMb: 256
  checkpointHeartbeat: true

state:
  directory: data
  transactionMemoryMb: 1024
```

配置规则：

- `includeTables` 必填，使用完整的 `PDB.OWNER.TABLE` Java 正则。
- `excludeTables` 优先于 include。
- `redoPathMappings` 使用最长 Oracle 路径前缀。
- 未知字段、重复映射、无效正则和逃出安装目录的相对路径会直接失败。
- 启动时尚不存在的匹配表会等待；后续 CREATE 提交后从该结构版本开始捕获。
- H2 已有状态时忽略 YAML 的 `startScn`，只能通过 `rewind.sh` 回退。

先验证，再启动：

```bash
bin/validate.sh
bin/start.sh
bin/status.sh
```

安全停止：

```bash
bin/stop.sh
```

`stop.sh` 只发送 SIGTERM，不会升级到 `kill -9`。进程在完整 LWN 边界完成
JSONL fsync 和 H2 提交后退出。

## 输出与恢复

输出文件名为 `output/redo-000001.jsonl`，只在完整 JSON 消息之间滚动。每个事务
按 begin、DML/DDL、commit 多行输出。消费者只有读到完整 commit 后才能应用事务。

恢复顺序固定为：

1. 从 H2 读取安全 redo 位置或最早未提交事务 low-watermark。
2. 校验 DBID、incarnation 和 RESETLOGS。
3. 将 JSONL 未提交尾部截断到 H2 已 fsync 字节位置。
4. 从安全位置重放；允许完整事务重复，不允许缺失。

如果 JSONL 实际长度小于 H2 安全偏移，程序停止，不猜测恢复位置。

## 运维脚本

```text
bin/run.sh                         前台运行
bin/start.sh                       后台运行
bin/stop.sh                        SIGTERM 安全停止
bin/restart.sh                     安全重启
bin/status.sh                      PID 与非权威状态快照
bin/validate.sh                    只做启动前检查
bin/backup.sh                      备份 H2、YAML 和状态
bin/restore.sh <backup-file>       校验身份后恢复
bin/rewind.sh --scn <SCN>          验证 redo/结构后回退安全位点
```

backup 不包含 JSONL 和 `data/tmp/`。restore 与 rewind 都先保留旧 H2 安全副本，
历史 JSONL 永不自动删除，新输出使用更大的文件编号。

## 日志

`logging.level` 支持 `TRACE`、`DEBUG`、`INFO`、`WARN` 和 `ERROR`。前台
`run.sh` 同时写终端与 `logs/redo-replicator.log`；后台 `start.sh` 只写主日志，
`console.log` 只保留启动器和直接 CLI 输出。主日志达到 100 MB 后滚动，保留 10 个
gzip 压缩历史文件。日志不会输出数据库密码。

## 开发构建

开发、测试和运行验证统一使用本地非 Temurin OpenJDK 17 与 Maven 3.9+。

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
mvn clean verify
```

显式执行 100,000 行单事务 spill 门禁：

```bash
mvn -q -Dredoreplicator.test.scale=true \
  -Dtest=RedoTransactionBufferScaleTest test
```

短性能 gate 默认处理 100,000 行；最终验收使用
`-Dredoreplicator.performance.minutes=30` 持续运行 30 分钟：

```bash
mvn -q -Dredoreplicator.test.performance=true \
  -Dtest=RedoPipelinePerformanceTest test
```

固定 C++ 基线提交为 `6bc92bc1b89255fbc491e3080cb12a4c1dd8e832`。构建基线：

```bash
scripts/baseline/prepare-rapidjson.sh target/rapidjson
scripts/baseline/build-openlogreplicator.sh \
  /Users/pika/codex-cli-worker/OpenLogReplicator target/rapidjson
```

迁移清单覆盖校验：

```bash
tools/verify-migration-map.sh /Users/pika/codex-cli-worker/OpenLogReplicator
```

Oracle 字典矩阵 fixture 由 SYSDBA/PDB 管理员手动执行，参数依次是 PDB、测试用户和
密码；清理脚本只需要 PDB 与测试用户：

```sql
@sql/test/create_dictionary_fixture.sql FREEPDB1 APP RedoFixture_26ai
@sql/test/drop_dictionary_fixture.sql FREEPDB1 APP
```

## 发行构建

本机架构自包含包：

```bash
scripts/release/build-distribution.sh
scripts/release/test-distribution.sh \
  target/distributions/redo-replicator-0.1.0-SNAPSHOT-*.tar.gz
```

固定 digest 的 Maven 3.9.16 + Amazon Corretto OpenJDK 17 Buildx 镜像会构建并
验证 Linux ARM64 和 x86_64 两个运行包：

```bash
scripts/release/build-linux-distributions.sh
scripts/release/build-source-distribution.sh
scripts/release/generate-checksums.sh
```

产物位于 `target/distributions/`，包括两个 Linux `.tar.gz`、sources、
CycloneDX 1.6 `SBOM.json` 和 `SHA256SUMS`。在迁移清单、Oracle 正确性矩阵、
故障注入与性能门禁全部通过前，不发布 `0.1.0`。

## 许可证

RedoReplicator 使用 AGPL-3.0-or-later。直接翻译的文件保留 OpenLogReplicator
版权与来源说明。运行包包含 `THIRD-PARTY-LICENSES/`、实际依赖 jar 内嵌条款、
Oracle FUTC、NOTICE 和 SBOM。
