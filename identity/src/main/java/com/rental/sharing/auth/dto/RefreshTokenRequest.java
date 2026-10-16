package com.rental.sharing.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank(message = "刷新令牌不能为空") @Size(max = 512, message = "刷新令牌过长")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String refreshToken) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[refreshToken=REDACTED]";
    }
}
