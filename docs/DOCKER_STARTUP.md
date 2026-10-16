# 容器启动 CLI 指南

当前服务为 PostgreSQL 与 Spring Boot backend，Flyway 迁移工具位于 `tools` profile。构建和运行均在容器中完成，部署机器只需 Docker 与 Docker Compose。根目录父工程直接包含 `common`、`identity`、`rental`、`app` 四个 Maven 模块，Dockerfile 和构建上下文也位于根目录。只有 `app` 生成可执行包，仍由一个 backend 容器运行。以下命令均在项目根目录执行。

认证、用户端房屋信息和模块架构测试在 `app/src/test/`，使用独立 `test` profile。准备好 `.env` 后执行 `docker compose --profile test run --rm auth-test`；沿用原服务名，命令执行 `mvn -pl app -am test`，从根目录父工程构建 app 及其依赖模块，并自动等待测试 PostgreSQL 健康。共24项测试，覆盖认证、房屋增改查删、权限与地址隐私、筛选分页及模块依赖。每个用例清理自身房屋记录、刷新令牌和账号；不会连接业务数据库。详细范围和 IDE 配置见 [统一接口测试](../README.md#72-统一接口测试)。

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

新机器需编辑 `.env`，将 `POSTGRES_PASSWORD` 的模板值改为长随机密码，并设置 `AUTH_SIGNING_KEY`。本机沿用原 PostgreSQL 密码和数据卷。

JWT 签名密钥是至少 32 字节随机数的 Base64 编码。已有 `.env` 仅补充缺失密钥，重建时保留原值。以下命令在容器内生成密钥并安全写入 `.env`，不输出密钥，也不会改动已有非空密钥：

```bash
key_file=$(mktemp)
chmod 600 "$key_file"
docker run --rm --entrypoint sh "${POSTGRES_IMAGE:-docker.m.daocloud.io/library/postgres:16-alpine}" -c 'head -c 32 /dev/urandom | base64' > "$key_file"
if [ -s "$key_file" ] && ! awk -F= '/^AUTH_SIGNING_KEY=./ { found=1 } END { exit !found }' .env; then
  (umask 077; sed '/^AUTH_SIGNING_KEY=/d' .env > .env.auth.tmp)
  printf 'AUTH_SIGNING_KEY=' >> .env.auth.tmp
  cat "$key_file" >> .env.auth.tmp
  chmod 600 .env.auth.tmp
  mv .env.auth.tmp .env
fi
rm -f "$key_file"
```

生成命令中的镜像应与 `.env` 的 `POSTGRES_IMAGE` 一致（必要时替换命令中的镜像名）。密钥不能写入 Git 或镜像；应用在密钥缺失、Base64 不合法或解码后不足 32 字节时拒绝启动。

| 配置项 | 作用 | 默认值 / 说明 |
| --- | --- | --- |
| `POSTGRES_DB` | 首次创建的数据库 | `rental` |
| `POSTGRES_USER` | 数据库账号 | `rental` |
| `POSTGRES_PASSWORD` | 数据库密码 | 必须设置，本机沿用原配置 |
| `POSTGRES_PORT` | 宿主机数据库端口 | `5432` |
| `APP_PORT` | 宿主机 HTTP 端口 | `8080` |
| `AUTH_SIGNING_KEY` | Base64 编码的随机 JWT 签名密钥 | 必填，解码后至少 32 字节，重建和迁移保留原值 |
| `AUTH_ISSUER` / `AUTH_AUDIENCE` | JWT 签发方 / 接收方 | `rental-info-sharing` / `rental-info-sharing-api` |
| `AUTH_ACCESS_TOKEN_TTL` / `AUTH_REFRESH_TOKEN_TTL` | 令牌有效期 | `30m` / `7d`，必须为正整数秒的时长 |
| `CORS_ALLOWED_ORIGINS` | 浏览器跨来源白名单 | 默认空，逗号分隔完整 origin，不允许通配符 |
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

`config --quiet` 检查配置但不输出展开后的密码。`--build` 在 Maven 容器中执行 `mvn -pl app -am verify`，构建依赖模块、运行 ArchUnit 架构检查，并将 `app/target/rental-info-sharing.jar` 放入运行镜像；数据库集成测试由 test profile 单独运行。`--wait` 等待数据库及应用健康检查通过。首次需要下载镜像及 Maven 依赖，后续使用缓存。

启动顺序为：PostgreSQL 就绪 → backend 启动 → Flyway 校验及迁移 → HTTP 健康检查。Flyway 由后端启动时自动运行，迁移文件统一位于 `app/src/main/resources/db/migration/`，tools profile 的迁移工具挂载同一目录。房屋信息功能复用现有 `rental_records` 表和索引，不新增迁移。原 V1 内容和 `public.flyway_schema_history` 保持不变，已成功执行的迁移只校验，不重复建表。本次不增加服务或环境变量，已有 `.env` 和命名数据卷可继续使用。

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

后端自动运行 Flyway，无需每次手动启动迁移工具。只验证构建时：

```bash
docker compose build backend
```

| 命令 | 作用 |
| --- | --- |
| `docker compose logs -f backend` | 持续查看后端日志，按 Ctrl+C 退出查看 |
| `docker compose stop` | 停止服务，保留容器和数据卷 |
| `docker compose down` | 移除服务容器和网络，保留 PostgreSQL 命名数据卷 |
| `docker compose up -d --build --wait` | 应用代码或配置变更并等待就绪 |

`restart` 不会应用 Compose 或 `.env` 变更；修改后使用 `up`。日常操作不要使用 `down -v`，它会删除数据库数据卷。备份、恢复时保留原 JWT 签名密钥；更换密钥会使旧 Access Token 失效。认证接口见 [README 的 API 说明](../README.md#7-api-设计)。备份、恢复及跨机器迁移见 [README](../README.md#93-备份恢复与跨机器迁移)。

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
