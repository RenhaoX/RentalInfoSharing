package com.rental.sharing.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                            long expiresIn, long refreshExpiresIn) {
    @Override
    public String toString() {
        return "TokenResponse[accessToken=REDACTED, refreshToken=REDACTED]";
    }
}
