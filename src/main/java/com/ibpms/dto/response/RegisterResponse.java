package com.ibpms.dto.response;

public record RegisterResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username,
        String email,
        String role,
        String departmentId,
        long expiresIn
) {}

