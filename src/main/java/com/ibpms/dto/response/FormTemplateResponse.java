package com.ibpms.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record FormTemplateResponse(
        String id,
        String name,
        String description,
        String createdBy,
        Map<String, Object> formSchema,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
