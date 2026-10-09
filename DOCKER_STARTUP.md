# 容器启动 CLI 指南

当前服务为 PostgreSQL 与 Spring Boot backend，Flyway 迁移工具位于 `tools` profile。构建、测试和运行均在容器中完成，部署机器只需 Docker 与 Docker Compose。

## 一、首次启动

在项目根目录执行，先确认 Docker 引擎已启动：

```bash
docker version
docker compose version
```

首次准备配置；已有 `.env` 时保留并参照模板补充缺少的应用配置，不覆盖原数据库密码：

```bash
if [ ! -f .env ]; then
  cp .env.example .env
fi
chmod 600 .env
```

新机器需编辑 `.env`，将 `POSTGRES_PASSWORD` 的模板值改为长随机密码。本机沿用原 PostgreSQL 密码和数据卷。

| 配置项 | 作用 | 默认值 / 说明 |
| --- | --- | --- |
| `POSTGRES_DB` | 首次创建的数据库 | `rental` |
| `POSTGRES_USER` | 数据库账号 | `rental` |
| `POSTGRES_PASSWORD` | 数据库密码 | 必须设置，本机沿用原配置 |
| `POSTGRES_PORT` | 宿主机数据库端口 | `5432` |
| `APP_PORT` | 宿主机 HTTP 端口 | `8080` |
| `POSTGRES_IMAGE` | PostgreSQL 镜像 | `docker.m.daocloud.io/library/postgres:16-alpine` |
| `FLYWAY_IMAGE` | 迁移工具镜像 | `docker.m.daocloud.io/flyway/flyway:13.10.0-alpine` |
| `MAVEN_IMAGE` | 构建镜像 | `docker.m.daocloud.io/library/maven:3.9.11-eclipse-temurin-21` |
| `JAVA_IMAGE` | 运行镜像 | `docker.m.daocloud.io/library/eclipse-temurin:21.0.12.1_1-jre` |

数据库初始化变量只在空数据卷首次启动时生效。已有数据后修改 `.env` 不会自动更新库内账号或密码。端口只绑定本机 `127.0.0.1`。

校验配置并启动：

```bash
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
```

`config --quiet` 检查配置但不输出展开后的密码。`--build` 在 Maven 容器中执行 `mvn verify`，测试通过才生成应用镜像；`--wait` 等待数据库及应用健康检查通过。首次需要下载镜像及 Maven 依赖，后续使用缓存。

启动顺序为：PostgreSQL 就绪 → backend 启动 → Flyway 校验及迁移 → HTTP 健康检查。Flyway 由后端启动时自动运行，原 V1 脚本和 `public.flyway_schema_history` 保持不变，已成功执行的迁移只校验，不重复建表。

## 二、验收

```bash
curl --fail http://localhost:8080/api/v1/health
```

端口以 `.env` 中的 `APP_PORT` 为准。成功时 HTTP 200，响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": { "status": "UP", "database": "UP" },
  "traceId": "e2f1..."
}
```

此接口通过 MyBatis-Plus / Mapper 执行真实的 `SELECT 1`，验证数据库连接。数据库不可用时返回 HTTP 503 和统一错误码 `50300`；响应包含 traceId，并提供 `X-Trace-Id` 响应头。

查询连接和迁移历史：

```bash
docker compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, version();" -c "SELECT version, description, checksum, success FROM public.flyway_schema_history;"'
docker compose --profile tools run --rm migrate validate
docker compose --profile tools run --rm migrate info
```

数据库应包含原有 5 张业务表和 `flyway_schema_history`，V1 的 `success` 应为 `true`。宿主机数据库工具使用 `127.0.0.1`、`POSTGRES_PORT` 和账号密码；应用容器使用 `postgres:5432`。

## 三、日常启动与排查

```bash
docker compose up -d --build --wait
docker compose ps
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
```

后端自动运行 Flyway，无需每次手动启动迁移工具。只验证构建和测试时：

```bash
docker compose build backend
```

| 命令 | 作用 |
| --- | --- |
| `docker compose logs -f backend` | 持续查看后端日志，按 Ctrl+C 退出查看 |
| `docker compose stop` | 停止服务，保留容器和数据卷 |
| `docker compose down` | 移除服务容器和网络，保留 PostgreSQL 命名数据卷 |
| `docker compose up -d --build --wait` | 应用代码或配置变更并等待就绪 |

`restart` 不会应用 Compose 或 `.env` 变更；修改后使用 `up`。日常操作不要使用 `down -v`，它会删除数据库数据卷。备份、恢复及跨机器迁移见 [README](README.md#93-备份恢复与跨机器迁移)。

## 四、镜像源切换

默认使用 DaoCloud，其他网络环境中可在 `.env` 中指定同版本官方镜像：

```dotenv
POSTGRES_IMAGE=postgres:16-alpine
FLYWAY_IMAGE=flyway/flyway:13.10.0-alpine
MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
JAVA_IMAGE=eclipse-temurin:21.0.12.1_1-jre
```

PostgreSQL、Maven 和 Java 也可使用官方镜像的 AWS 公共来源，将前缀改为 `public.ecr.aws/docker/library/`，保持版本相同。之后重新执行：

```bash
docker compose config --quiet
docker compose up -d --build --wait
```

切换来源时保持 PostgreSQL 主版本为 16，并继续使用原 `pgdata` 数据卷。
