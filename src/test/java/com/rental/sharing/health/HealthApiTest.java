package com.rental.sharing.health;

import com.rental.sharing.common.exception.GlobalExceptionHandler;
import com.rental.sharing.common.web.TraceIdFilter;
import com.rental.sharing.health.controller.HealthController;
import com.rental.sharing.health.mapper.HealthMapper;
import com.rental.sharing.health.service.HealthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 在 MVC 测试上下文中验证统一响应、HTTP 错误映射、参数校验和 traceId。
 * 保留真实 HealthService，仅替换数据库 Mapper，测试不依赖外部 PostgreSQL。
 */
@WebMvcTest(HealthController.class)
@Import({HealthService.class, GlobalExceptionHandler.class, TraceIdFilter.class, HealthApiTest.ValidationController.class})
class HealthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthMapper healthMapper;

    @Test
    void healthyDatabaseReturnsUnifiedResponseAndTraceId() throws Exception {
        when(healthMapper.ping()).thenReturn(1);

        var response = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.traceId").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{32}")))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Trace-Id"));
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }

    @Test
    void databaseFailureReturns503WithoutLeakingDetails() throws Exception {
        when(healthMapper.ping()).thenThrow(new DataAccessResourceFailureException("private connection details"));

        var response = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(50300))
                .andExpect(jsonPath("$.message").value("数据库暂时不可用"))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private connection details");
    }

    @Test
    void unexpectedFailureReturnsSafe500Response() throws Exception {
        when(healthMapper.ping()).thenThrow(new IllegalStateException("private internal details"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(50000))
                .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }

    @Test
    void missingRouteReturnsUnified404() throws Exception {
        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void unsupportedMethodReturnsUnified405() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    void invalidBodyReturnsValidationMessage() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("名称不能为空"));
    }

    @Test
    void malformedJsonReturnsUnified400() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void unsupportedContentTypeReturnsUnified415() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(MediaType.TEXT_PLAIN).content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(41500));
    }

    /** 仅在测试上下文中注册，用于验证请求体校验，不提供生产接口。 */
    @RestController
    static class ValidationController {

        @PostMapping("/test/validation")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }
    }

    record ValidationRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
