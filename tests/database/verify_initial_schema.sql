\set ON_ERROR_STOP on

-- 仅在专用临时库执行；所有约束测试数据最终回滚。
BEGIN;

DO $$
BEGIN
    IF current_database() !~ '^rental_schema_test_[a-z0-9_]+$' THEN
        RAISE EXCEPTION '测试仅允许在 rental_schema_test_* 临时数据库执行';
    END IF;
END;
$$;

CREATE FUNCTION pg_temp.expect_sqlstate(statement TEXT, expected_state TEXT)
RETURNS void
LANGUAGE plpgsql
AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE = expected_state THEN
            RETURN;
        END IF;
        RAISE;
    END;
    RAISE EXCEPTION '期望 SQLSTATE %，但语句执行成功：%', expected_state, statement;
END;
$$;

DO $$
DECLARE
    user_a BIGINT;
    user_b BIGINT;
    code_id BIGINT;
    token_id BIGINT;
    rental_id BIGINT;
    business_tables TEXT[] := ARRAY['users', 'user_bindings', 'verification_codes', 'refresh_tokens', 'rental_records'];
BEGIN
    IF (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ANY(business_tables)) <> 5 THEN
        RAISE EXCEPTION '应存在 5 张业务表';
    END IF;
    IF (SELECT count(*) FROM public.flyway_schema_history WHERE version = '1' AND success) <> 1 THEN
        RAISE EXCEPTION 'Flyway V1 应成功执行一次';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = ANY(business_tables)
          AND obj_description(c.oid, 'pg_class') IS NULL
    ) OR EXISTS (
        SELECT 1 FROM pg_attribute a JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = ANY(business_tables)
          AND a.attnum > 0 AND NOT a.attisdropped AND col_description(c.oid, a.attnum) IS NULL
    ) THEN
        RAISE EXCEPTION '业务表或字段缺少注释';
    END IF;

    INSERT INTO public.users (username, password_hash, updated_at)
    VALUES ('schema_test_a', 'test-only-hash', '2000-01-01 00:00:00+00') RETURNING id INTO user_a;
    INSERT INTO public.users (username, password_hash)
    VALUES ('schema_test_b', 'test-only-hash') RETURNING id INTO user_b;
    IF NOT (SELECT status = 1 AND created_at IS NOT NULL AND updated_at = created_at FROM public.users WHERE id = user_b) THEN
        RAISE EXCEPTION '用户默认值不正确';
    END IF;
    PERFORM pg_temp.expect_sqlstate('INSERT INTO public.users (username, password_hash) VALUES (''schema_test_a'', ''hash'')', '23505');
    PERFORM pg_temp.expect_sqlstate('INSERT INTO public.users (username) VALUES (''missing_password'')', '23502');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.users SET status = 2 WHERE id = %s', user_a), '23514');
    UPDATE public.users SET nickname = '更新昵称', status = 0 WHERE id = user_a;
    IF NOT (SELECT updated_at = now() AND updated_at > '2000-01-01 00:00:00+00'::TIMESTAMPTZ FROM public.users WHERE id = user_a) THEN
        RAISE EXCEPTION '用户更新时间触发器未生效';
    END IF;

    INSERT INTO public.user_bindings (user_id, bind_type, bind_value)
    VALUES (user_a, 'PHONE', '13800000000'), (user_a, 'EMAIL', 'schema_test@example.com');
    IF EXISTS (SELECT 1 FROM public.user_bindings WHERE user_id = user_a AND (verified OR created_at IS NULL)) THEN
        RAISE EXCEPTION '绑定记录默认值不正确';
    END IF;
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (%s, ''PHONE'', ''13900000000'')', user_a), '23505');
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (%s, ''EMAIL'', ''other@example.com'')', user_a), '23505');
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (%s, ''PHONE'', ''13800000000'')', user_b), '23505');
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (%s, ''EMAIL'', ''schema_test@example.com'')', user_b), '23505');
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (%s, ''OTHER'', ''value'')', user_a), '23514');
    PERFORM pg_temp.expect_sqlstate('INSERT INTO public.user_bindings (user_id, bind_type, bind_value) VALUES (-1, ''PHONE'', ''13700000000'')', '23503');

    INSERT INTO public.verification_codes (scene, target, code_hash, expires_at)
    VALUES ('BIND_PHONE', '13800000000', repeat('a', 64), now() + interval '5 minutes') RETURNING id INTO code_id;
    IF NOT (SELECT attempt_count = 0 AND used_at IS NULL AND created_at IS NOT NULL FROM public.verification_codes WHERE id = code_id) THEN
        RAISE EXCEPTION '验证码默认值不正确';
    END IF;
    UPDATE public.verification_codes SET attempt_count = 5 WHERE id = code_id;
    UPDATE public.verification_codes SET scene = 'BIND_EMAIL' WHERE id = code_id;
    UPDATE public.verification_codes SET scene = 'RESET_PWD' WHERE id = code_id;
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.verification_codes SET attempt_count = 6 WHERE id = %s', code_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.verification_codes SET attempt_count = -1 WHERE id = %s', code_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.verification_codes SET scene = ''OTHER'' WHERE id = %s', code_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.verification_codes SET code_hash = NULL WHERE id = %s', code_id), '23502');

    INSERT INTO public.refresh_tokens (user_id, token_hash, expires_at)
    VALUES (user_a, repeat('b', 64), now() + interval '7 days') RETURNING id INTO token_id;
    IF NOT (SELECT NOT revoked AND created_at IS NOT NULL FROM public.refresh_tokens WHERE id = token_id) THEN
        RAISE EXCEPTION '刷新令牌默认值不正确';
    END IF;
    PERFORM pg_temp.expect_sqlstate(format('INSERT INTO public.refresh_tokens (user_id, token_hash, expires_at) VALUES (%s, repeat(''b'', 64), now())', user_b), '23505');
    PERFORM pg_temp.expect_sqlstate('INSERT INTO public.refresh_tokens (user_id, token_hash, expires_at) VALUES (-1, repeat(''c'', 64), now())', '23503');

    INSERT INTO public.rental_records (user_id, city, address, monthly_rent, rating, rent_start_date, rent_end_date, updated_at)
    VALUES (user_a, '杭州', '测试地址', 0, 1, '2026-01-01', '2026-01-01', '2000-01-01 00:00:00+00') RETURNING id INTO rental_id;
    INSERT INTO public.rental_records (user_id, city, address) VALUES (user_b, '北京', '可选字段为空的地址');
    IF NOT (SELECT status = 1 AND deleted_at IS NULL AND created_at IS NOT NULL FROM public.rental_records WHERE id = rental_id) THEN
        RAISE EXCEPTION '租房记录默认值不正确';
    END IF;
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET rating = 0 WHERE id = %s', rental_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET rating = 6 WHERE id = %s', rental_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET monthly_rent = -0.01 WHERE id = %s', rental_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET rent_end_date = ''2025-12-31'' WHERE id = %s', rental_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET status = 2 WHERE id = %s', rental_id), '23514');
    PERFORM pg_temp.expect_sqlstate(format('UPDATE public.rental_records SET city = NULL WHERE id = %s', rental_id), '23502');
    PERFORM pg_temp.expect_sqlstate('INSERT INTO public.rental_records (user_id, city, address) VALUES (-1, ''杭州'', ''测试地址'')', '23503');
    UPDATE public.rental_records SET content = '测试更新', rating = 5, monthly_rent = 5000, status = 0, deleted_at = now() WHERE id = rental_id;
    IF NOT (SELECT updated_at = now() AND updated_at > '2000-01-01 00:00:00+00'::TIMESTAMPTZ FROM public.rental_records WHERE id = rental_id) THEN
        RAISE EXCEPTION '租房更新时间触发器未生效';
    END IF;
    PERFORM pg_temp.expect_sqlstate(format('DELETE FROM public.users WHERE id = %s', user_a), '23503');
END;
$$;

ROLLBACK;
\echo 'Initial schema constraint, default, comment and trigger checks passed; test rows rolled back.'
