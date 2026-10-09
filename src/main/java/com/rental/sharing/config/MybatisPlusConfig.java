package com.rental.sharing.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 注册各功能域中标记了 {@link Mapper} 的数据访问接口。
 * 限定注解类型，避免将 Service 等普通接口误注册为 Mapper。
 */
@Configuration
@MapperScan(basePackages = "com.rental.sharing", annotationClass = Mapper.class)
public class MybatisPlusConfig {
}
