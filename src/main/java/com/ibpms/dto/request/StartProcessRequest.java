package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StartProcessRequest(
        @NotBlank String policyId,
        Map<String, Object> initialData,
        String clientId
) {}

