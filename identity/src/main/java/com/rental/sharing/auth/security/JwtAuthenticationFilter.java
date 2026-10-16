package com.rental.sharing.auth.security;

import com.rental.sharing.auth.service.JwtService;
import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SecurityErrorWriter errorWriter;
    private final RequestMatcher publicEndpoints;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityErrorWriter errorWriter, RequestMatcher publicEndpoints) {
        this.jwtService = jwtService;
        this.errorWriter = errorWriter;
        this.publicEndpoints = publicEndpoints;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 刷新不能依赖已过期的 Access Token，公共接口完全由各自的凭据认证。
        return publicEndpoints.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            try {
                if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    throw new BusinessException(ErrorCode.TOKEN_INVALID);
                }
                var principal = jwtService.verify(header.substring(7));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (BusinessException exception) {
                SecurityContextHolder.clearContext();
                errorWriter.write(response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
