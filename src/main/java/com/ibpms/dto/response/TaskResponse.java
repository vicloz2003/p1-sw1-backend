package com.ibpms.dto.response;

import com.ibpms.domain.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record TaskResponse(
        String id,
        String nodeId,
        String nodeLabel,
        String processInstanceId,
        String assignedDepartmentId,
        TaskStatus status,
        Map<String, Object> formSchema,
        LocalDateTime assignedAt
) {}

