package com.rental.sharing.health.dto;

/**
 * 健康检查成功时的响应数据；数据库故障通过错误响应表达。
 *
 * @param status   应用整体状态，当前成功时为 UP
 * @param database 数据库连通状态，当前成功时为 UP
 */
public record HealthStatus(String status, String database) {
}
