package com.rental.sharing.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rental.sharing.auth.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CredentialsRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{3,31}", message = "用户名须为4～32位字母、数字或下划线，并以字母开头")
        String username,
        @ValidPassword @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password) {
    @Override
    public String toString() {
        return "CredentialsRequest[password=REDACTED]";
    }
}
