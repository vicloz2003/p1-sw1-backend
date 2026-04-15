package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AssignDepartmentRequest(
        @NotBlank String departmentId
) {}

