package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body for {@code POST /api/v1/agent/classify} (RF-2.1).
 * The client's free-text description of their problem (already transcribed from
 * voice if applicable). The backend attaches the active policies and delegates
 * classification to ibpms_ml.
 */
public record AgentClassifyRequest(
        @NotBlank(message = "text is required")
        String text
) {}
