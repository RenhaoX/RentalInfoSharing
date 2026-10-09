# RentalInfoSharing 租房信息共享平台

> 架构设计文档 v0.1 · 本机验证版（Local MVP）
> 后端栈：Java 21 + Spring Boot 3.5.x + PostgreSQL · 前端选型待定（API First）

---

## 1. 项目背景与目标

一个由用户共建的租房信息共享平台：用户填写自己**实际租住过**的房子（城市、地址、租赁平台、租期、体验与踩坑等），供其他人参考，减少信息差。

**本阶段目标**：在本机跑通一套端到端验证版，覆盖「登录 → 信息绑定 → 租房信息填写/浏览」核心链路，验证产品形态后再决定前端选型与上线方案。

**设计原则**

- API First：后端对外只暴露 REST + OpenAPI 文档，前端可随时替换（React / Vue / 小程序均可对接）。
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
| 通用能力 | 统一响应、统一异常、参数校验、OpenAPI 文档 | 所有接口具备 |

**明确不做（后续演进）**：图片上传、评论点赞、地图、举报审核、微信/短信真实通道、消息通知。

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────┐
│                    前端（待定）                       │
│        React / Vue / 小程序 · 通过 REST API 对接      │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP (JSON) / JWT
┌──────────────────────▼──────────────────────────────┐
│                 Spring Boot 应用                     │
│  ┌───────────┐  ┌───────────┐  ┌─────────────────┐  │
│  │ JWT 过滤器 │→ │ Controller │→ │ Service（业务）  │  │
│  └───────────┘  └───────────┘  └────────┬────────┘  │
│       横切：统一响应 / 全局异常 / 参数校验 / 日志     │
│                                ┌────────▼────────┐  │
│                                │ Repository(JPA) │  │
│                                └────────┬────────┘  │
└─────────────────────────────────────────┼───────────┘
                                          │ JDBC
                                 ┌────────▼────────┐
                                 │  PostgreSQL 16  │
                                 │  (Docker 容器)   │
                                 └─────────────────┘
```

**分层职责**

| 层 | 职责 | 约束 |
| --- | --- | --- |
| Controller | 参数接收与校验、鉴权注解、组装响应 | 不写业务逻辑，不直接访问 Repository |
| Service | 业务逻辑、事务边界、权限判断 | 返回 DTO，不暴露 Entity |
| Repository | 数据访问（Spring Data JPA） | 复杂查询可用 @Query 手写 JPQL |
| Entity/DTO | 持久化模型 / 传输模型 | 严格分离，避免直接序列化 Entity |

---

## 4. 技术选型

| 类别 | 选型 | 理由 |
| --- | --- | --- |
| 语言 | Java 21（LTS） | 虚拟线程、记录类，Spring Boot 3.x 官方基线 |
| 框架 | Spring Boot 3.5.x | 生态成熟，面试/维护友好 |
| Web | Spring MVC + Jackson | REST 标准方案 |
| 安全 | Spring Security + JWT（jjwt 0.12.x） | 无状态鉴权，前后端分离友好 |
| ORM | Spring Data JPA (Hibernate) | 快速建模，复杂查询可回退原生 SQL |
| 数据库 | PostgreSQL 16（Docker） | 生产级，JSONB/数组等能力为后续扩展留空间 |
| 迁移 | Flyway | 版本化 DDL，团队协作可追溯 |
| 校验 | Jakarta Validation | 声明式参数校验 |
| 文档 | springdoc-openapi 2.x | 自动生成 Swagger UI，前端联调依据 |
| 构建 | Maven（spring-boot-starter-parent） | 简单稳定 |
| 测试 | JUnit 5 + MockMvc（单元）/ Testcontainers（集成，可选） | 保证核心链路可回归 |
| 前端 | **待定** | 接口契约已由 OpenAPI 固化，后端先行不受影响 |

---

## 5. 后端模块与目录结构

单模块 Maven 工程，按**功能域分包**（package-by-feature），便于后续拆微服务：

```
RentalInfoSharing/
├── README.md
├── docker-compose.yml              # PostgreSQL 与 Flyway 迁移工具
├── .env.example                    # 部署配置模板，实际密码放在 .env
├── pom.xml
└── src/main/java/com/rental/sharing/
    ├── RentalInfoSharingApplication.java
    ├── common/                     # 通用能力
    │   ├── api/                    # ApiResponse、PageResult、错误码
    │   ├── exception/              # BizException、全局异常处理
    │   └── util/                   # 脱敏、时间等工具
    ├── config/                     # SecurityConfig、OpenApiConfig、JacksonConfig
    ├── auth/                       # 认证域
    │   ├── controller/AuthController.java
    │   ├── service/AuthService.java
    │   ├── dto/                    # RegisterReq、LoginReq、TokenResp...
    │   └── security/               # JwtProvider、JwtAuthFilter
    ├── user/                       # 用户域
    │   ├── controller/UserController.java
    │   ├── controller/BindingController.java
    │   ├── service/UserService.java
    │   ├── service/BindingService.java
    │   ├── entity/User.java / UserBinding.java / RefreshToken.java
    │   ├── repository/
    │   └── dto/
    └── rental/                     # 租房信息域
        ├── controller/RentalController.java
        ├── service/RentalService.java
        ├── entity/RentalRecord.java
        ├── repository/RentalRecordRepository.java
        └── dto/
└── src/main/resources/
    ├── application.yml             # 公共配置
    ├── application-dev.yml         # 本机开发配置
    └── db/migration/V1__init.sql   # Flyway 脚本
```

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

> 启动后可访问 `http://localhost:8080/swagger-ui.html` 查看完整 OpenAPI 文档。

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

**当前状态**：已提供 PostgreSQL、容器化 Flyway 和 V1 建表脚本；后端 Maven 工程和前端源码尚未创建。第 5 节应用目录和 API 为待实现设计，当前没有可启动的后端/前端容器。

按顺序执行的 CLI 命令及作用见 [容器启动指南](DOCKER_STARTUP.md)。

### 9.1 启动数据库

宿主机安装 Docker 与 Docker Compose 即可。首次在新机器部署时：

```bash
cp .env.example .env
# 编辑 .env，将 POSTGRES_PASSWORD 改为长随机密码。
docker compose up -d --wait postgres
docker compose --profile tools run --rm migrate
docker compose --profile tools run --rm migrate validate
docker compose ps
```

本次本机安装已生成随机密码的 `.env`，无需再次复制模板。实际密码和备份文件均已加入 `.gitignore`，不会提交到 Git。

配置见 `docker-compose.yml`：PostgreSQL 16、健康检查、自动重启和命名数据卷 `pgdata`。数据卷独立于容器，重建容器或执行 `docker compose down` 都会保留数据。`docker compose down -v` 会删除数据卷，不用于日常停止服务。

**镜像源**：默认使用 DaoCloud 国内镜像源 `docker.m.daocloud.io/library/postgres:16-alpine`。本机已验证拉取成功，镜像摘要与 AWS 公共仓库的 Docker 官方镜像一致。国内源不可用或迁移到其他网络环境时，在 `.env` 中设置 `POSTGRES_IMAGE=public.ecr.aws/docker/library/postgres:16-alpine`，也可使用官方地址 `POSTGRES_IMAGE=postgres:16-alpine`，然后重新拉取并启动：

```bash
docker compose pull postgres
docker compose up -d --wait postgres
```

**初始化与版本化迁移**：Flyway 工具服务位于 `tools` profile，使用固定版本 `13.10.0-alpine`，支持 ARM64/AMD64，默认经 DaoCloud 拉取。需要切换来源时，在 `.env` 设置 `FLYWAY_IMAGE=flyway/flyway:13.10.0-alpine`。

```bash
docker compose --profile tools run --rm migrate
docker compose --profile tools run --rm migrate validate
docker compose --profile tools run --rm migrate info
```

以上依次执行待处理迁移、校验已执行脚本和查看版本历史。脚本目录为 `src/main/resources/db/migration`，只读挂载到迁移容器；数据库连接通过环境变量注入，密码不写入脚本。

V1 创建 5 张业务表，Flyway 另建 `flyway_schema_history` 保存版本和校验和。重复执行 `migrate` 会跳过已成功执行的版本；后续改动新增 `V2__*.sql` 等脚本，已执行的 V1 保持不变。自动 baseline 和 `clean` 已禁用，已有未知表时不会自动认领或清空数据库。首次迁移前确认目标为空，后续迁移前检查已有历史。

约束、默认值、字段注释和触发器的验证脚本为 `tests/database/verify_initial_schema.sql`，仅允许在 `rental_schema_test_*` 临时库执行，测试数据通过事务回滚。迁移和恢复数据时先在临时库验证，再操作业务库。

镜像源通过项目配置切换。切换来源时核对镜像摘要和 CPU 架构，并保持相同的 PostgreSQL 主版本及数据卷；跨主版本升级需另行执行数据库升级或备份恢复流程。

数据库名称、用户、密码和本机端口由 `.env` 配置，默认数据库/用户为 `rental`，地址为 `127.0.0.1:5432`。如端口冲突，可修改 `POSTGRES_PORT`。数据库默认只向本机开放，应用容器通过 Compose 内部网络连接。

`POSTGRES_DB`、`POSTGRES_USER`、`POSTGRES_PASSWORD` 仅在空数据卷首次初始化时创建数据库与账号。已有数据后修改 `.env` 不会自动修改库内密码，需通过 SQL 修改并同步应用配置。

常用命令：

```bash
docker compose logs -f postgres
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose stop
docker compose up -d --wait postgres
```

### 9.2 后续应用模块的部署方式

后端采用 Java 21 多阶段 Docker 构建，在构建容器内运行 Maven，最终镜像只包含 JRE 和应用 JAR；前端选型后也添加独立 Dockerfile 和 Compose 服务。认证、用户、租房信息目前设计为同一个 Spring Boot 应用内的功能模块，随同一个后端容器部署。

后端容器通过以下环境变量连接数据库，并通过 `depends_on` 的 `service_healthy` 条件等待 PostgreSQL 就绪：

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rental
SPRING_DATASOURCE_USERNAME=rental
SPRING_DATASOURCE_PASSWORD=<与 .env 的 POSTGRES_PASSWORD 一致>
```

容器内数据库主机名使用服务名 `postgres`。宿主机调试才使用 `localhost` 和 `.env` 中的 `POSTGRES_PORT`。后端数据库驱动使用 Maven 依赖 `org.postgresql:postgresql`，随应用镜像构建安装；不需要 Node.js 的 `pg` 包。后端共用现有迁移脚本和 `flyway_schema_history`，JPA 使用 `ddl-auto: validate` 校验表结构。

### 9.3 备份与迁移

在源机器项目目录中备份数据库（每次保存为独立文件）：

```bash
mkdir -p backups
backup_file="backups/rental-$(date +%Y%m%d-%H%M%S).dump"
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_file"
```

将项目文件和生成的 `.dump` 文件搬到目标机器，按 9.1 节配置 `.env` 并启动 PostgreSQL，再恢复到一个空数据库（将文件名替换为实际备份名）：

```bash
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error' < backups/rental-YYYYMMDD-HHMMSS.dump
```

恢复备份前仅启动目标机器的 PostgreSQL，保持目标数据库为空，先完成恢复，再执行 Flyway `validate` 和 `migrate`；不要在恢复前运行第 9.1 节的建表命令。备份包含业务结构、数据和 Flyway 版本历史。

Git 不包含 `.env` 和备份，迁移时需单独配置/传输。命名数据卷不会随代码自动搬迁；跨机器、跨 CPU 架构迁移使用上述逻辑备份与恢复。切换机器前停止应用写入并做最后一次备份，恢复后验证数据再切换应用流量。

---

## 10. 里程碑计划（单人 · 按半天粒度估）

| 阶段 | 内容 | 产出 | 预估 |
| --- | --- | --- | --- |
| M0 | 工程脚手架：Maven、Docker Compose、Flyway V1、统一响应/异常、OpenAPI | 服务可启动，健康检查通过 | 0.5 天 |
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
6. **工程化**：在现有 Docker Compose 基础上接入后端/前端镜像，后续增加 Redis 缓存与限流、CI/CD、日志与监控（Actuator + Prometheus）。

---

*本文档为 v0.1 验证版设计，随开发推进持续更新。*
