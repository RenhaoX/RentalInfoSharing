package com.rental.sharing.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rental.sharing.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT id, username, password_hash, nickname, status, created_at FROM users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);
}
