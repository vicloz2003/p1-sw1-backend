package com.ibpms.dto.response;

/** Speech-to-text result proxied from ibpms_ml (RF-2.2). */
public record TranscribeResponse(
        String text,
        String language,
        double duration
) {}
