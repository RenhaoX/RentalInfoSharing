package com.rental.sharing.health.controller;

import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.health.dto.HealthStatus;
import com.rental.sharing.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ApiResponse<HealthStatus> health() {
        return ApiResponse.success(healthService.check());
    }
}
