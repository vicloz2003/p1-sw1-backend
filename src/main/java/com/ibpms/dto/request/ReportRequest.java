package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * A manager's natural-language report request (RF-4). The text may come from typing or from
 * the existing speech-to-text endpoint (voice → text) on the client side.
 *
 * <p>{@code format} overrides whatever format Gemini infers from the instruction. Allowed
 * values: {@code SCREEN} (JSON, default), {@code EXCEL}, {@code WORD}, {@code PDF}.
 */
public record ReportRequest(
        @NotBlank String instruction,
        String format
) {}
