package com.rental.sharing.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(@NotBlank String signingKey,
                             @NotBlank String issuer,
                             @NotBlank String audience,
                             @NotNull Duration accessTokenTtl,
                             @NotNull Duration refreshTokenTtl) {
    @Override
    public String toString() {
        return "AuthProperties[signingKey=REDACTED]";
    }
}
