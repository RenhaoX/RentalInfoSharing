package com.rental.sharing.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        write(new ServletServerHttpResponse(response), errorCode);
    }

    public void write(ServerHttpResponse response, ErrorCode errorCode) throws IOException {
        response.setStatusCode(errorCode.getHttpStatus());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        if (errorCode.getHttpStatus().value() == 401) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getBody(), ApiResponse.error(errorCode, errorCode.getMessage()));
        response.flush();
    }
}
