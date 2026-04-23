package com.ibpms.dto.response;

public record UserResponse(
        String id,
        String username,
        String email,
        String role,
        String departmentId,
        boolean enabled
) {}

