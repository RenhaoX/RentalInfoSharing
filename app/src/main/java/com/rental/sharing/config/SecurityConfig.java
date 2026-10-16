package com.rental.sharing.config;

import com.rental.sharing.auth.config.AuthProperties;
import com.rental.sharing.auth.security.JwtAuthenticationFilter;
import com.rental.sharing.auth.security.SecurityErrorWriter;
import com.rental.sharing.auth.service.JwtService;
import com.rental.sharing.common.api.ErrorCode;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {
    @Bean
    Clock authClock() { return Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, SecurityErrorWriter errorWriter) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var publicEndpoints = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, "/api/v1/auth/register"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/login"),
                paths.matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
                paths.matcher(HttpMethod.GET, "/api/v1/health"));
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> errorWriter.write(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) -> errorWriter.write(response, ErrorCode.FORBIDDEN)))
                // 直接构造，仅加入 Security 链，避免同时注册为 Servlet Filter 执行两次。
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, errorWriter, publicEndpoints),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(origin -> !origin.isEmpty()).toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("CORS_ALLOWED_ORIGINS 必须列出具体来源，不能使用通配符");
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("X-Trace-Id", "WWW-Authenticate"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource, SecurityErrorWriter errorWriter) {
        var filter = new CorsFilter(corsConfigurationSource);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override
            protected void rejectRequest(ServerHttpResponse response) throws IOException {
                errorWriter.write(response, ErrorCode.FORBIDDEN);
            }
        });
        return filter;
    }

    @Bean
    FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsFilter corsFilter) {
        // CORS 仅在 Security 链内执行，保留外层 TraceIdFilter 的顺序。
        var registration = new FilterRegistrationBean<>(corsFilter);
        registration.setEnabled(false);
        return registration;
    }
}
