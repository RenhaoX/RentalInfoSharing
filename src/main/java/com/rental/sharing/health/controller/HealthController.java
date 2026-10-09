package com.rental.sharing.health.controller;

import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.health.dto.HealthStatus;
import com.rental.sharing.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用及数据库健康检查入口，同时供 Docker Compose 判断后端是否就绪。
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** 数据库查询成功返回 HTTP 200；不可用时由全局异常处理器返回 HTTP 503。 */
    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(healthService.check());
    }
}
