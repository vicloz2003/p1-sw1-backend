package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * A manager's natural-language report request (RF-4). The text may come from typing or from
 * the existing speech-to-text endpoint (voice → text) on the client side.
 */
public record ReportRequest(
        @NotBlank String instruction
) {}
