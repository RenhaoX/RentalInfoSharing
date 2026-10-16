package com.rental.sharing.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.sharing.auth.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
    @Select("SELECT id, user_id, token_hash, expires_at, revoked, created_at FROM refresh_tokens WHERE token_hash = #{hash}")
    RefreshToken findByHash(@Param("hash") String hash);

    // PostgreSQL 会在并发更新等待结束后重新检查条件，保证同一令牌最多被消费一次。
    @Update("UPDATE refresh_tokens SET revoked = true WHERE id = #{id} AND revoked = false AND expires_at > now()")
    int consume(@Param("id") long id);

    @Update("UPDATE refresh_tokens SET revoked = true WHERE id = #{id} AND user_id = #{userId}")
    int revoke(@Param("id") long id, @Param("userId") long userId);
}
