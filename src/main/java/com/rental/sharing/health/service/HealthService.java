package com.rental.sharing.health.service;

import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import com.rental.sharing.health.dto.HealthStatus;
import com.rental.sharing.health.mapper.HealthMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** 使用真实数据库查询判断就绪状态，并将数据访问异常转换为统一业务错误。 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    /** 仅当数据库返回预期结果时报告 UP，避免只检查应用进程而误判整体健康。 */
    public HealthStatus check() {
        try {
            if (healthMapper.ping() != 1) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
            }
            return new HealthStatus("UP", "UP");
        } catch (DataAccessException exception) {
            // 连接异常详情留在服务端，客户端只收到统一的数据库不可用提示。
            log.warn("数据库健康检查失败", exception);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
