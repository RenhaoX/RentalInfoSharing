# RentalInfoSharing 租房信息共享平台

> 架构与部署文档 v0.2 · 第一阶段基础工程已实现
> 后端栈：Java 21 + Spring Boot 3.5.16 + MyBatis-Plus 3.5.17 + PostgreSQL 16 · 前端选型待定（API First）

---

## 1. 项目背景与目标

一个由用户共建的租房信息共享平台：用户填写自己**实际租住过**的房子（城市、地址、租赁平台、租期、体验与踩坑等），供其他人参考，减少信息差。

**当前阶段已实现**：可启动的 Spring Boot 单模块 Maven 工程，接入现有 PostgreSQL、MyBatis-Plus、Flyway 13.10.0，提供统一响应、请求 traceId、全局异常处理和数据库健康检查。标准启动入口为 `docker compose up -d --build --wait`。

**后续 MVP 目标**：覆盖「登录 → 信息绑定 → 租房信息填写/浏览」核心链路；下文认证、用户、租房接口及安全设计为后续规划，当前只实现健康检查接口。

**设计原则**

- API First：后端对外提供 REST，OpenAPI 文档后续接入，前端可随时替换（React / Vue / 小程序均可对接）。
- 最小依赖：统一使用 Docker Compose 部署；当前外部服务只有 PostgreSQL，不引入 Redis / MQ / 对象存储。
- 容器化部署（全项目强制准则）：所有当前及未来模块都通过容器部署，统一使用 Docker Compose 编排，构建工具与运行时也纳入镜像；宿主机只需 Docker 与 Docker Compose。
- 生产可迁移：数据结构、鉴权、分层方式按可上线标准设计，验证成功后可平滑演进。

**全局开发准则：所有模块容器化部署，便于迁移。** 该要求贯穿整个项目的技术选型、开发和交付，适用于后端、前端、管理端、定时任务及数据库、缓存等依赖服务。按可独立运行的应用或服务划分容器，同一应用内的功能模块随所属应用一起部署。

具体执行要求记录在项目根目录的 [AGENTS.md](AGENTS.md)：构建与运行环境容器化、Compose 统一编排、配置外置、数据持久化与备份恢复、版本与 CPU 架构兼容，以及新增模块时同步维护部署配置并验证。部署和迁移操作见第 9 节。

---

## 2. 验证版功能范围（MVP）

| 模块 | 功能 | 说明 |
| --- | --- | --- |
| 认证登录 | 注册、登录、刷新令牌、登出 | 用户名 + 密码注册；支持用户名/手机号/邮箱登录 |
| 信息绑定 | 绑定/解绑手机号、邮箱 | 验证码验证；手机号/邮箱可作登录凭证与联系方式 |
| 租房信息 | 发布、编辑、删除、详情、列表筛选 | 核心字段：城市、地址、租赁平台、房东类型、租期、租金、体验评价 |
| 个人中心 | 查看/修改昵称等基础资料 | 查看自己发布的记录 |
| 通用能力 | 统一响应、统一异常、参数校验；OpenAPI 文档后续接入 | 基础能力已实现 |

**明确不做（后续演进）**：图片上传、评论点赞、地图、举报审核、微信/短信真实通道、消息通知。

---

## 3. 总体架构

```text
前端（后续） → REST API → Controller → Service → Mapper → PostgreSQL 16
                         统一响应 / 全局异常 / 参数校验 / traceId
```

JWT 鉴权和业务域属于后续阶段；当前健康检查经过同样的 Controller → Service → Mapper 分层。

**分层职责**

| 层 | 职责 | 约束 |
| --- | --- | --- |
| Controller | 参数接收与校验、鉴权注解、组装响应 | 不写业务逻辑，不直接访问 Mapper |
| Service | 业务逻辑、事务边界、权限判断 | 返回 DTO，不暴露 Entity |
| Mapper | 数据访问（MyBatis-Plus） | CRUD 使用 BaseMapper，复杂查询使用 XML 或 SQL 注解 |
| Entity/DTO | 持久化模型 / 传输模型 | 严格分离，避免直接序列化 Entity |

---

## 4. 技术选型

| 类别 | 选型 | 理由 |
| --- | --- | --- |
| 语言 | Java 21（LTS） | 虚拟线程、记录类，Spring Boot 3.x 官方基线 |
| 框架 | Spring Boot 3.5.16 | 生态成熟，面试/维护友好 |
| Web | Spring MVC + Jackson | REST 标准方案 |
| 安全（后续） | Spring Security + JWT（jjwt 0.12.x） | 无状态鉴权，前后端分离友好 |
| ORM | MyBatis-Plus 3.5.17 | 提供基础 CRUD 与 MyBatis SQL 映射 |
| 数据库 | PostgreSQL 16（Docker） | 生产级，JSONB/数组等能力为后续扩展留空间 |
| 迁移 | Flyway 13.10.0 | 版本化 DDL，团队协作可追溯 |
| 校验 | Jakarta Validation | 声明式参数校验 |
| 文档（后续） | springdoc-openapi 2.x | 自动生成 Swagger UI，前端联调依据 |
| 构建 | Maven（spring-boot-starter-parent） | 简单稳定 |
| 测试 | JUnit 5 + MockMvc + Compose 实机验收 | 构建时执行测试，运行时验证真实 PostgreSQL 连接 |
| 前端 | **待定** | 后端先行，接口按 REST 契约迭代 |

---

## 5. 后端模块与目录结构

单模块 Maven 工程，按**功能域分包**（package-by-feature），便于后续拆微服务：

```
RentalInfoSharing/
├── pom.xml                         # Java 21 / Spring Boot / MyBatis-Plus
├── Dockerfile                      # Maven 构建（含测试）+ JRE 运行
├── .dockerignore                   # 排除密钥、备份和本地构建产物
├── docker-compose.yml              # PostgreSQL + backend，迁移工具位于 tools profile
├── .env.example                    # 外置配置模板
├── README.md / DOCKER_STARTUP.md
├── src/main/java/com/rental/sharing/
│   ├── RentalInfoSharingApplication.java
│   ├── common/
│   │   ├── api/                    # ApiResponse、ErrorCode
│   │   ├── exception/              # BusinessException、GlobalExceptionHandler
│   │   └── web/                    # TraceIdFilter
│   ├── config/                     # MybatisPlusConfig：扫描 @Mapper
│   └── health/
│       ├── controller/             # GET /api/v1/health
│       ├── service/                # 数据库连通性检查
│       ├── mapper/                 # SELECT 1，经 MyBatis-Plus 数据访问链路
│       └── dto/                    # HealthStatus
├── src/main/resources/
│   ├── application.yml             # 数据源、Flyway、MyBatis-Plus 和日志
│   └── db/migration/V1__init.sql    # 原 PostgreSQL 脚本，内容保持不变
├── src/test/java/                  # 健康接口、异常和参数校验测试
└── tests/database/verify_initial_schema.sql
```

后续按功能域增加 `auth`、`user`、`rental` 包，每个域使用 controller/service/mapper/entity/dto 分层；当前不添加尚未实现的业务接口。

---

## 6. 数据库设计

### 6.1 ER 关系

```
users 1 ──── n user_bindings         （每个账号最多一个手机号和一个邮箱）
users 1 ──── n refresh_tokens        （登录会话）
users 1 ──── n rental_records        （用户发布的租房记录）
verification_codes 按 scene + target 查询（验证码不直接关联 users）
```

### 6.2 表结构

**users 用户表**

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGSERIAL | PK | |
| username | VARCHAR(32) | UNIQUE NOT NULL | 登录名 |
| password_hash | VARCHAR(100) | NOT NULL | BCrypt |
| nickname | VARCHAR(32) | | 昵称 |
| avatar_url | VARCHAR(255) | | 预留 |
| status | SMALLINT | NOT NULL DEFAULT 1, CHECK 0/1 | 1 正常 / 0 禁用 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | updated_at 由触发器维护 |

**user_bindings 绑定表**（登录凭证 & 联系方式）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK → users NOT NULL | |
| bind_type | VARCHAR(16) | NOT NULL, CHECK PHONE/EMAIL | PHONE / EMAIL |
| bind_value | VARCHAR(128) | NOT NULL | 手机号 / 邮箱 |
| verified | BOOLEAN | NOT NULL DEFAULT false | 是否已验证 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | |
| — | — | UNIQUE(bind_type, bind_value) | 一个手机号/邮箱只能绑一个账号 |
| — | — | UNIQUE(user_id, bind_type) | 每个账号最多绑定一个手机号和一个邮箱 |

**verification_codes 验证码表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGSERIAL PK | |
| scene | VARCHAR(24) | NOT NULL，CHECK BIND_PHONE / BIND_EMAIL / RESET_PWD |
| target | VARCHAR(128) | NOT NULL，手机号 / 邮箱 |
| code_hash | VARCHAR(64) | NOT NULL，验证码哈希（不存明文） |
| expires_at | TIMESTAMPTZ | NOT NULL，由业务设置 5 分钟有效期 |
| used_at | TIMESTAMPTZ | 使用后置位 |
| attempt_count | SMALLINT | NOT NULL DEFAULT 0，CHECK 0~5，最多试错 5 次 |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

> 本地验证版：验证码「发送」走 Mock 通道，dev profile 下写入日志，方便本机调试。

**refresh_tokens 刷新令牌表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGSERIAL PK | |
| user_id | BIGINT FK NOT NULL | 所属用户 |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL，存储 SHA-256 哈希 |
| expires_at | TIMESTAMPTZ | NOT NULL，由业务设置 7 天有效期 |
| revoked | BOOLEAN | NOT NULL DEFAULT false，登出/轮换后置 true |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() |

**rental_records 租房记录表**（核心业务表）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | BIGSERIAL | PK | |
| user_id | BIGINT | FK NOT NULL | 发布者 |
| city | VARCHAR(32) | NOT NULL | 城市，如「杭州」 |
| district | VARCHAR(32) | | 区县，如「西湖区」 |
| address | VARCHAR(255) | NOT NULL | 详细地址（列表页脱敏） |
| community_name | VARCHAR(64) | | 小区名 |
| rental_platform | VARCHAR(64) | | 租赁平台：贝壳/自如/链家/个人房东… |
| landlord_type | VARCHAR(16) | | 房东直租 / 二房东 / 中介 / 品牌公寓 |
| room_type | VARCHAR(16) | | 整租 / 合租 / 主卧 / 次卧 |
| monthly_rent | NUMERIC(10,2) | CHECK >= 0 | 月租金（元） |
| deposit_desc | VARCHAR(32) | | 押付方式，如「押一付三」 |
| rent_start_date | DATE | | 入住日期 |
| rent_end_date | DATE | CHECK >= rent_start_date | 搬离日期 |
| rating | SMALLINT | CHECK 1~5 | 综合体验评分 |
| highlights | TEXT | | 优点 |
| pitfalls | TEXT | | 踩坑/注意事项 |
| content | TEXT | | 详细经验描述 |
| status | SMALLINT | NOT NULL DEFAULT 1, CHECK 0/1 | 1 已发布 / 0 已下架 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | updated_at 由触发器维护 |
| deleted_at | TIMESTAMPTZ | | 软删除 |

**索引**

```sql
CREATE INDEX idx_rental_city_created  ON rental_records (city, created_at DESC);
CREATE INDEX idx_rental_platform      ON rental_records (rental_platform);
CREATE INDEX idx_rental_user          ON rental_records (user_id);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens (user_id);
CREATE INDEX idx_verification_codes_scene_target_created
    ON verification_codes (scene, target, created_at DESC);
```

唯一约束自动创建索引，包括用户名、绑定类型/值、用户/绑定类型和刷新令牌哈希，不再重复建索引。外键限制删除仍有绑定、令牌或租房记录的用户；租房记录使用 `deleted_at` 软删除。所有业务表及字段均提供中文注释。

---

## 7. API 设计

**统一响应格式**

```json
{
  "code": 0,
  "message": "ok",
  "data": { },
  "traceId": "e2f1..."
}
```

`code = 0` 表示成功；业务错误码见 `common/api/ErrorCode`（如 40101 未登录、40102 令牌过期、40901 手机号已被绑定、40401 记录不存在）。

**认证域** `/api/v1/auth`

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| POST | /register | 注册（username + password） | 否 |
| POST | /login | 登录，返回 accessToken + refreshToken | 否 |
| POST | /refresh | 刷新令牌（refresh 轮换） | 否 |
| POST | /logout | 登出，吊销 refreshToken | 是 |

**用户与绑定域** `/api/v1/users`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /me | 当前用户信息（含昵称、已绑定项） |
| PATCH | /me | 修改昵称等资料 |
| POST | /me/bindings/send-code | 发送验证码 `{type, target}` |
| POST | /me/bindings/verify | 提交验证码完成绑定 `{type, target, code}` |
| DELETE | /me/bindings/{type} | 解绑（需至少保留一种登录凭证） |

**租房信息域** `/api/v1/rentals`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | / | 发布记录 |
| GET | /{id} | 详情（地址完整展示；仅发布者可编辑） |
| GET | /?city=&platform=&landlordType=&keyword=&page=&size= | 分页列表（地址脱敏） |
| PUT | /{id} | 编辑（仅发布者） |
| DELETE | /{id} | 软删除（仅发布者） |
| GET | /mine | 我发布的记录 |

> 以上业务接口和 Swagger UI 尚未实现；当前可用接口为 `GET /api/v1/health`，详见第 9 节。

---

## 8. 安全设计

| 项 | 方案 |
| --- | --- |
| 密码存储 | BCrypt（cost 10），禁止明文/可逆加密 |
| 令牌 | Access Token 30 分钟（JWT，内存传递）+ Refresh Token 7 天（存哈希、可吊销、轮换） |
| 接口鉴权 | Spring Security 过滤器链，白名单仅 /auth/** 与文档路径 |
| 越权防护 | Service 层校验资源归属（如只有发布者可改删） |
| 参数校验 | Jakarta Validation + 全局异常统一转错误码 |
| 验证码 | 哈希存储、5 分钟过期、试错上限、同 target 发送频率限制（本地版内存计数） |
| 地址脱敏 | 列表页隐藏门牌号，详情页展示完整 |
| CORS | dev profile 放开 localhost 前端端口，生产白名单 |
| 敏感日志 | 密码、验证码、令牌一律不落日志 |

---

## 9. Docker 部署与迁移

完整命令及说明见 [容器启动指南](DOCKER_STARTUP.md)。宿主机只需 Docker 与 Docker Compose，不需要本地 Java、Maven 或 PostgreSQL。

### 9.1 启动与验收

首次在新机器部署：

```bash
cp .env.example .env
# 编辑 .env，将 POSTGRES_PASSWORD 改为长随机密码。
chmod 600 .env
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
curl --fail http://localhost:8080/api/v1/health
```

已有 `.env` 时保留原文件，参照模板补充缺少的应用配置。本机沿用原 PostgreSQL 账号密码、容器服务名 `postgres` 和 `pgdata` 数据卷。数据库与应用端口默认只绑定 `127.0.0.1`，分别通过 `POSTGRES_PORT` 和 `APP_PORT` 调整。

健康接口通过 Mapper 实际执行 `SELECT 1`。连接成功时返回 HTTP 200：

```json
{
  "code": 0,
  "message": "ok",
  "data": { "status": "UP", "database": "UP" },
  "traceId": "e2f1..."
}
```

数据库不可用时返回 HTTP 503，`code=50300`、`message="数据库暂时不可用"`、`data=null`；未知接口返回 HTTP 404 / `code=40400`。每个请求生成 traceId，同时写入响应体、`X-Trace-Id` 响应头和日志；异常详情记录在服务端，响应不暴露堆栈或连接信息。

backend 等待 PostgreSQL healthy 后启动，Flyway 自动校验并迁移，然后由 HTTP 健康检查判断应用和数据库整体是否就绪。迁移失败时应用启动失败。镜像构建执行 `mvn verify`（含测试），最终以非 root 用户运行 JRE 和 JAR。

IDE 调试时使用 Java 21，并设置 `SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 和 `SPRING_DATASOURCE_URL`（例如 `jdbc:postgresql://localhost:5432/rental?connectTimeout=3&socketTimeout=5`）；Spring Boot 不会自动读取根目录 `.env`。

### 9.2 数据源与 Flyway

应用容器连接 `postgres:5432`；宿主机数据库工具使用 `127.0.0.1` 和 `.env` 中的 `POSTGRES_PORT`。账号默认 `rental`，密码从 `.env` 注入，不写入代码或镜像。

Flyway 13.10.0 扫描 `classpath:db/migration`，沿用原 V1 脚本及 `public.flyway_schema_history`。已执行的 V1 只校验，不重复建表；后续变更新增 `V2__*.sql`，不要修改已执行的迁移。自动 baseline 和 clean 已禁用，不自动认领或清空未知数据库。

```bash
docker compose exec -T postgres sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user, version();" -c "SELECT version, description, checksum, success FROM public.flyway_schema_history;"'
docker compose --profile tools run --rm migrate validate
docker compose --profile tools run --rm migrate info
```

后端启动时自动执行迁移，无需每次另跑 migrate。容器化迁移工具保留在 `tools` profile，版本与后端一致，可用于独立校验或临时库验证。

命名数据卷 `pgdata` 独立保存数据库，容器重建或 `docker compose down` 后数据仍保留。初始化变量仅在空数据卷首次启动时创建数据库与账号；已有数据后修改 `.env` 不会自动更新库内密码。

约束、默认值、字段注释和触发器验证脚本为 `tests/database/verify_initial_schema.sql`，仅允许在 `rental_schema_test_*` 临时库执行，测试数据通过事务回滚。

### 9.3 备份、恢复与跨机器迁移

备份包含业务结构、数据与 Flyway 历史。备份前停止应用写入，数据库继续运行：

```bash
docker compose stop backend
mkdir -p backups
backup_file="backups/rental-$(date +%Y%m%d-%H%M%S).dump"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_file"
docker compose up -d --build --wait
```

检查命令退出码和备份文件非空后，搬迁项目代码、编排文件和备份。`.env` 和备份不提交到 Git，目标机器单独配置密码。命名数据卷不会随代码搬迁，跨机器及 ARM64/AMD64 迁移使用逻辑备份。

目标机器先配置 `.env`，只启动 PostgreSQL，确认数据库为空后恢复；不要先启动 backend 创建表：

```bash
docker compose up -d --wait postgres
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error' < backups/rental-YYYYMMDD-HHMMSS.dump
docker compose up -d --build --wait
curl --fail http://localhost:8080/api/v1/health
```

恢复后核对表数量、关键数据、Flyway 历史和接口状态，再切换流量。日常停止使用 `docker compose stop` 或 `docker compose down`；`down -v` 会删除数据卷，不能用于普通重启。

### 9.4 镜像来源

默认使用 DaoCloud，运行时与依赖采用明确版本，镜像支持 ARM64 / AMD64。其他网络环境可在 `.env` 中设置同版本官方来源：

```dotenv
POSTGRES_IMAGE=postgres:16-alpine
FLYWAY_IMAGE=flyway/flyway:13.10.0-alpine
MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-21
JAVA_IMAGE=eclipse-temurin:21.0.12.1_1-jre
```

PostgreSQL、Maven 和 Java 镜像也可使用 `public.ecr.aws/docker/library/` 前缀。本机 Maven 与 Java 已使用此备用来源。部署路径相对于项目目录，不依赖本机用户路径或运行时。切换镜像来源时保持 PostgreSQL 主版本为 16，并继续使用原数据卷；跨主版本升级应另外制定数据库升级或备份恢复流程。

---

## 10. 里程碑计划（单人 · 按半天粒度估）

| 阶段 | 内容 | 产出 | 预估 |
| --- | --- | --- | --- |
| M0（已实现） | Maven、Docker Compose、PostgreSQL、MyBatis-Plus、Flyway V1、统一响应/异常、健康检查 | 服务可启动，健康检查通过；OpenAPI 后续补充 | 已完成 |
| M1 | 认证：注册、登录、JWT 过滤器、刷新、登出 | 全链路可拿到令牌访问受保护接口 | 1 天 |
| M2 | 信息绑定：验证码 Mock 通道、绑定/解绑、登录凭证扩展 | 手机号/邮箱可绑定并用于登录 | 1 天 |
| M3 | 租房信息：CRUD、分页筛选、归属校验、地址脱敏 | 核心业务闭环 | 1.5 天 |
| M4 | 联调验证：接口测试补全、README 接口示例、Postman/curl 脚本 | 可交付前端联调 | 1 天 |

---

## 11. 后续演进路线

1. **前端定选**：依据 OpenAPI 生成 TypeScript SDK，React/Vue 均可；上线前补 SSR/SEO 评估。
2. **内容生态**：图片上传（对象存储）、评论/点赞、举报与审核后台。
3. **检索增强**：城市/区县/平台/价格区间多条件组合筛选；后期引入 Elasticsearch 或 PG 全文检索。
4. **地图能力**：地址地理编码，地图聚合展示（高德/腾讯地图）。
5. **账号体系**：微信/支付宝第三方登录，真实短信/邮件通道。
6. **工程化**：在现有 Docker Compose 基础上接入前端镜像，后续增加 Redis 缓存与限流、CI/CD、日志与监控（Actuator + Prometheus）。

---

*本文档区分已实现基础工程与后续业务规划，随开发推进持续更新。*
