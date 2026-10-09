package com.rental.sharing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 租房信息共享平台启动入口。
 * 位于项目根包，使 Spring Boot 自动扫描各功能域与公共组件。
 */
@SpringBootApplication
public class RentalInfoSharingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalInfoSharingApplication.class, args);
    }
}
