package com.ibpms.dto.response;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}

