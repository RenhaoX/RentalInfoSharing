package com.rental.sharing.health.service;

import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import com.rental.sharing.health.dto.HealthStatus;
import com.rental.sharing.health.mapper.HealthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    public HealthStatus check() {
        try {
            if (healthMapper.ping() != 1) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return new HealthStatus("UP", "UP");
        } catch (DataAccessException exception) {
            log.warn("数据库健康检查失败", exception);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
