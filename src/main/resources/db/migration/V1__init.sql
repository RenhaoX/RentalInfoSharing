-- 初始业务结构由 Flyway 在同一个事务中创建。
-- 已执行的迁移保持不变，后续结构调整使用新的版本脚本。

CREATE TABLE public.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(32),
    avatar_url VARCHAR(255),
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (status IN (0, 1))
);

CREATE TABLE public.user_bindings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bind_type VARCHAR(16) NOT NULL,
    bind_value VARCHAR(128) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_bindings_user FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT uq_user_bindings_type_value UNIQUE (bind_type, bind_value),
    CONSTRAINT uq_user_bindings_user_type UNIQUE (user_id, bind_type),
    CONSTRAINT ck_user_bindings_type CHECK (bind_type IN ('PHONE', 'EMAIL'))
);

CREATE TABLE public.verification_codes (
    id BIGSERIAL PRIMARY KEY,
    scene VARCHAR(24) NOT NULL,
    target VARCHAR(128) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_verification_codes_scene CHECK (scene IN ('BIND_PHONE', 'BIND_EMAIL', 'RESET_PWD')),
    CONSTRAINT ck_verification_codes_attempt_count CHECK (attempt_count BETWEEN 0 AND 5)
);

CREATE TABLE public.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE TABLE public.rental_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    city VARCHAR(32) NOT NULL,
    district VARCHAR(32),
    address VARCHAR(255) NOT NULL,
    community_name VARCHAR(64),
    rental_platform VARCHAR(64),
    landlord_type VARCHAR(16),
    room_type VARCHAR(16),
    monthly_rent NUMERIC(10, 2),
    deposit_desc VARCHAR(32),
    rent_start_date DATE,
    rent_end_date DATE,
    rating SMALLINT,
    highlights TEXT,
    pitfalls TEXT,
    content TEXT,
    status SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_rental_records_user FOREIGN KEY (user_id) REFERENCES public.users (id),
    CONSTRAINT ck_rental_records_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_rental_records_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_rental_records_monthly_rent CHECK (monthly_rent >= 0),
    CONSTRAINT ck_rental_records_dates CHECK (rent_end_date >= rent_start_date)
);

CREATE INDEX idx_rental_city_created ON public.rental_records (city, created_at DESC);
CREATE INDEX idx_rental_platform ON public.rental_records (rental_platform);
CREATE INDEX idx_rental_user ON public.rental_records (user_id);
CREATE INDEX idx_refresh_tokens_user ON public.refresh_tokens (user_id);
CREATE INDEX idx_verification_codes_scene_target_created ON public.verification_codes (scene, target, created_at DESC);

CREATE FUNCTION public.update_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

CREATE TRIGGER trg_rental_records_updated_at
    BEFORE UPDATE ON public.rental_records
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at();

COMMENT ON FUNCTION public.update_updated_at() IS '更新记录时自动维护 updated_at';

COMMENT ON TABLE public.users IS '用户账号与基础资料';
COMMENT ON COLUMN public.users.id IS '用户主键';
COMMENT ON COLUMN public.users.username IS '唯一登录用户名';
COMMENT ON COLUMN public.users.password_hash IS 'BCrypt 密码哈希，不保存明文密码';
COMMENT ON COLUMN public.users.nickname IS '用户昵称';
COMMENT ON COLUMN public.users.avatar_url IS '头像地址，预留字段';
COMMENT ON COLUMN public.users.status IS '账号状态：1 正常，0 禁用';
COMMENT ON COLUMN public.users.created_at IS '创建时间';
COMMENT ON COLUMN public.users.updated_at IS '更新时间，由数据库触发器维护';

COMMENT ON TABLE public.user_bindings IS '用户手机号与邮箱绑定，每种类型最多绑定一个';
COMMENT ON COLUMN public.user_bindings.id IS '绑定记录主键';
COMMENT ON COLUMN public.user_bindings.user_id IS '所属用户主键';
COMMENT ON COLUMN public.user_bindings.bind_type IS '绑定类型：PHONE 手机号，EMAIL 邮箱';
COMMENT ON COLUMN public.user_bindings.bind_value IS '手机号或邮箱，同一类型的值全局唯一';
COMMENT ON COLUMN public.user_bindings.verified IS '是否已验证，默认未验证';
COMMENT ON COLUMN public.user_bindings.created_at IS '绑定记录创建时间';

COMMENT ON TABLE public.verification_codes IS '验证码验证记录，按场景与目标查询';
COMMENT ON COLUMN public.verification_codes.id IS '验证码记录主键';
COMMENT ON COLUMN public.verification_codes.scene IS '验证场景：BIND_PHONE、BIND_EMAIL、RESET_PWD';
COMMENT ON COLUMN public.verification_codes.target IS '验证目标，手机号或邮箱';
COMMENT ON COLUMN public.verification_codes.code_hash IS '验证码哈希，不保存明文验证码';
COMMENT ON COLUMN public.verification_codes.expires_at IS '过期时间，业务通常设置为创建后 5 分钟';
COMMENT ON COLUMN public.verification_codes.used_at IS '成功使用时间，未使用时为空';
COMMENT ON COLUMN public.verification_codes.attempt_count IS '已尝试次数，范围 0 至 5';
COMMENT ON COLUMN public.verification_codes.created_at IS '创建时间';

COMMENT ON TABLE public.refresh_tokens IS '登录刷新令牌与吊销状态';
COMMENT ON COLUMN public.refresh_tokens.id IS '刷新令牌记录主键';
COMMENT ON COLUMN public.refresh_tokens.user_id IS '所属用户主键';
COMMENT ON COLUMN public.refresh_tokens.token_hash IS '唯一的 SHA-256 令牌哈希，不保存明文令牌';
COMMENT ON COLUMN public.refresh_tokens.expires_at IS '过期时间，业务通常设置为创建后 7 天';
COMMENT ON COLUMN public.refresh_tokens.revoked IS '是否已吊销，登出或令牌轮换后置为 true';
COMMENT ON COLUMN public.refresh_tokens.created_at IS '创建时间';

COMMENT ON TABLE public.rental_records IS '用户实际租住过的房屋信息与体验记录';
COMMENT ON COLUMN public.rental_records.id IS '租房记录主键';
COMMENT ON COLUMN public.rental_records.user_id IS '发布用户主键';
COMMENT ON COLUMN public.rental_records.city IS '城市';
COMMENT ON COLUMN public.rental_records.district IS '区县';
COMMENT ON COLUMN public.rental_records.address IS '详细地址，列表页由应用进行脱敏';
COMMENT ON COLUMN public.rental_records.community_name IS '小区名称';
COMMENT ON COLUMN public.rental_records.rental_platform IS '租赁平台或渠道';
COMMENT ON COLUMN public.rental_records.landlord_type IS '房东类型，如直租、二房东、中介、品牌公寓';
COMMENT ON COLUMN public.rental_records.room_type IS '房间类型，如整租、合租、主卧、次卧';
COMMENT ON COLUMN public.rental_records.monthly_rent IS '月租金，单位元，不能为负';
COMMENT ON COLUMN public.rental_records.deposit_desc IS '押付方式说明';
COMMENT ON COLUMN public.rental_records.rent_start_date IS '入住日期';
COMMENT ON COLUMN public.rental_records.rent_end_date IS '搬离日期，不能早于入住日期';
COMMENT ON COLUMN public.rental_records.rating IS '综合体验评分，范围 1 至 5';
COMMENT ON COLUMN public.rental_records.highlights IS '优点';
COMMENT ON COLUMN public.rental_records.pitfalls IS '踩坑与注意事项';
COMMENT ON COLUMN public.rental_records.content IS '详细租住经验';
COMMENT ON COLUMN public.rental_records.status IS '发布状态：1 已发布，0 已下架';
COMMENT ON COLUMN public.rental_records.created_at IS '创建时间';
COMMENT ON COLUMN public.rental_records.updated_at IS '更新时间，由数据库触发器维护';
COMMENT ON COLUMN public.rental_records.deleted_at IS '软删除时间，未删除时为空';
