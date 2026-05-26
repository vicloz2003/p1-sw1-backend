package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateFormTemplateRequest(
        @NotBlank String name,
        String description,
        @NotNull Map<String, Object> formSchema
) {}
