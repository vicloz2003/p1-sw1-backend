package com.ibpms.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username,
        String email,
        String role,
        String departmentId,
        long expiresIn
) {}

