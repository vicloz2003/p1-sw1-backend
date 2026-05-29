package com.ibpms.dto.response;

import com.ibpms.domain.enums.InstanceStatus;

/**
 * WebSocket payload pushed to /topic/process/{processInstanceId} when
 * the workflow engine advances the process to a new node or completes it.
 */
public record ProcessStateNotificationDto(
        String processInstanceId,
        String currentNodeId,
        String currentNodeLabel,
        String policyName,
        InstanceStatus status
) {}
