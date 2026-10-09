# 容器启动 CLI 指南

本指南按执行顺序说明 RentalInfoSharing 的容器启动命令及作用。当前有 PostgreSQL 常驻服务和 Flyway 迁移工具；后续前端、后端及其他服务接入后，统一使用相同的 Compose 启动入口。Flyway 位于 `tools` profile，仅在运行迁移命令时启动。

部署机器需要 Docker 引擎和 Docker Compose。应用的构建工具及运行环境由容器提供。

## 一、首次启动：按顺序执行

### 1. 进入项目目录

```bash
cd /Users/Xiang/PersonalProjects/RentalInfoSharing
```

**作用**：使后续 Compose 命令读取本项目的 `docker-compose.yml` 和 `.env`。迁移到其他机器时，改为目标机器的实际项目目录。

### 2. 启动 Docker 引擎

本机使用 OrbStack：

```bash
orbctl start
```

**作用**：启动 OrbStack 及其 Docker 引擎。已启动时可跳过此步；使用 Docker Desktop 或 Linux Docker 的机器，应先启动对应的 Docker 引擎。

### 3. 检查 Docker 与 Compose

```bash
docker version
docker compose version
```

**作用**：确认 Docker 客户端、服务端及 Compose 可用。`docker version` 应同时显示 `Client` 和 `Server`；如果提示无法连接服务端，先完成第 2 步。

### 4. 准备部署配置

```bash
if [ ! -f .env ]; then
  cp .env.example .env
fi
chmod 600 .env
```

**作用**：首次部署时从模板创建 `.env`，保留已有配置，并将文件权限限制为当前用户可读写。本机已生成随机密码的 `.env`，会直接沿用。

新机器首次复制模板后，使用编辑器设置密码：

```bash
vi .env
```

**作用**：将 `POSTGRES_PASSWORD` 的模板值改为长随机密码，并按需调整以下配置。使用 `vi` 时，按 `i` 编辑，完成后按 `Esc`，输入 `:wq` 并回车保存退出。

| 配置项 | 作用 | 默认值 / 说明 |
| --- | --- | --- |
| `POSTGRES_IMAGE` | PostgreSQL 镜像地址 | `docker.m.daocloud.io/library/postgres:16-alpine`，DaoCloud 国内源 |
| `FLYWAY_IMAGE` | 数据库迁移工具镜像 | `docker.m.daocloud.io/flyway/flyway:13.10.0-alpine`，DaoCloud 国内源 |
| `POSTGRES_DB` | 首次初始化创建的数据库 | `rental` |
| `POSTGRES_USER` | 首次初始化创建的数据库用户 | `rental` |
| `POSTGRES_PASSWORD` | 数据库密码 | 新机器必须替换模板值；本机已配置随机密码 |
| `POSTGRES_PORT` | 映射到宿主机的端口 | `5432`；端口冲突时可改为 `15432` 等空闲端口 |

数据库名、用户名和密码仅在空数据卷首次初始化时生效。已有数据后修改 `.env` 不会自动修改数据库内的账号或密码。

### 5. 校验 Compose 配置

```bash
docker compose --profile tools config --quiet
```

**作用**：检查 YAML、环境变量引用和必填配置。成功时无输出；如有错误，先修正再继续。`--quiet` 避免将展开后的密码打印到终端。

### 6. 拉取数据库与迁移工具镜像

```bash
docker compose --profile tools pull postgres migrate
```

**作用**：按照 `POSTGRES_IMAGE` 和 `FLYWAY_IMAGE` 拉取数据库及迁移工具镜像，默认使用 DaoCloud 国内源。已有相同镜像层时会复用本地缓存。

### 7. 启动项目容器

```bash
docker compose up -d --build --wait --wait-timeout 60
```

**作用**：创建或更新项目网络、数据卷和容器，并等待服务就绪。当前启动 PostgreSQL；后续加入其他服务后，此命令会一起启动它们。

| 参数 | 作用 |
| --- | --- |
| `up` | 创建并启动 Compose 中定义的服务 |
| `-d` | 在后台运行，命令结束后容器继续运行 |
| `--build` | 启动前构建配置了 `build` 的自研应用镜像；当前 PostgreSQL 直接使用已拉取的镜像 |
| `--wait` | 等待服务运行；配置了健康检查的服务必须通过健康检查 |
| `--wait-timeout 60` | 最多等待 60 秒；超时后需检查容器状态和日志 |

### 8. 创建或升级数据库表

```bash
docker compose --profile tools run --rm migrate
docker compose --profile tools run --rm migrate validate
```

**作用**：第一个命令执行尚未处理的 Flyway 迁移，首次创建 5 张业务表和 `flyway_schema_history`；第二个命令检查迁移脚本与已记录的校验和是否一致。`--profile tools` 启用工具服务，`run` 创建一次性迁移容器，`--rm` 在执行后移除该工具容器，数据库和数据卷继续保留。

首次建表前确认数据库为空；已有未知表时先排查，不自动 baseline。每次部署执行此步骤，已成功执行的迁移会跳过。后续新增版本脚本，已执行的脚本保持不变；自动 baseline 和 `clean` 已禁用。

如果从数据库备份迁移到新机器，此步骤应在恢复完成后执行：先启动 PostgreSQL，将备份恢复到空库，再校验和迁移，避免已创建的表与备份冲突。

### 9. 查看启动状态

```bash
docker compose ps
```

**作用**：查看服务状态和端口映射。当前 `postgres` 应显示 `Up ... (healthy)`，默认映射为 `127.0.0.1:5432->5432/tcp`。

### 10. 验证数据库连接与表结构

```bash
docker compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, version();"'
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt public.*"'
docker compose --profile tools run --rm migrate info
```

**作用**：依次验证密码连接、列出业务表与 Flyway 历史表、查看迁移版本。默认数据库和用户均为 `rental`；这些命令只查询信息。第三方数据库工具刷新 `public` schema 后即可看到表和中文字段注释。

宿主机数据库工具使用 `127.0.0.1`、`.env` 中的端口及账号密码连接。后续应用容器使用 Compose 服务名 `postgres` 和容器端口 `5432` 连接数据库。

## 二、日常重新启动

完成首次配置后，进入项目目录，确认 Docker 引擎运行，再执行：

```bash
docker compose up -d --build --wait --wait-timeout 60
docker compose --profile tools run --rm migrate
docker compose --profile tools run --rm migrate validate
docker compose ps
```

**作用**：启动或更新项目服务，应用新增迁移并检查状态。已有可用镜像时无需每次重新拉取。

## 三、日志、停止与重启

| 命令 | 作用 |
| --- | --- |
| `docker compose logs --tail=100 postgres` | 查看 PostgreSQL 最近 100 行日志，用于启动排查 |
| `docker compose logs -f postgres` | 持续查看数据库日志；按 `Ctrl+C` 退出查看，容器继续运行 |
| `docker compose stop` | 停止全部项目服务，保留容器、网络和数据卷 |
| `docker compose restart postgres` | 重启现有数据库容器；不会应用修改后的 Compose 或 `.env` 配置，也不会等待健康检查通过 |
| `docker compose down` | 停止并移除项目容器和网络，保留命名数据卷 |

修改部署配置后使用第 7 步的 `up` 命令应用变更。日常停止服务使用 `stop` 或 `down`；`docker compose down -v` 会删除数据卷和数据库数据。

## 四、国内镜像源不可用时

编辑 `.env`，将 PostgreSQL 改为 AWS 公共仓库来源，Flyway 改为官方 Docker Hub 来源：

```dotenv
POSTGRES_IMAGE=public.ecr.aws/docker/library/postgres:16-alpine
FLYWAY_IMAGE=flyway/flyway:13.10.0-alpine
```

再按顺序执行：

```bash
docker compose --profile tools config --quiet
docker compose --profile tools pull postgres migrate
docker compose up -d --build --wait --wait-timeout 60
docker compose --profile tools run --rm migrate
docker compose --profile tools run --rm migrate validate
docker compose ps
```

**作用**：校验配置、拉取备用来源镜像、应用变更并检查状态。切换来源时核对镜像摘要和 CPU 架构，保持 PostgreSQL 主版本为 16，并继续使用原数据卷。

备份、恢复与跨机器迁移步骤见 [README.md 的 Docker 部署与迁移章节](README.md#9-docker-部署与迁移)；全项目开发准则见 [AGENTS.md](AGENTS.md)。
