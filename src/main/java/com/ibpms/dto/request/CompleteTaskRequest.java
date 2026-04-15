package com.ibpms.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CompleteTaskRequest(
        @NotNull Map<String, Object> formData
) {}

