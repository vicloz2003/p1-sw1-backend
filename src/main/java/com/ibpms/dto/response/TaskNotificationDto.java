package com.ibpms.dto.response;

public record TaskNotificationDto(
        String taskId,
        String nodeId,
        String nodeLabel,
        String processInstanceId,
        String policyName
) {}

