package com.rental.sharing.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 通过实际的数据访问链路检查 PostgreSQL 连通性，不依赖业务表或测试数据。 */
@Mapper
public interface HealthMapper {

    /** 执行无副作用的查询，验证连接池、JDBC 驱动与 MyBatis 映射是否正常。 */
    @Select("SELECT 1")
    int ping();
}
