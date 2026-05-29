package com.ibpms.dto.response;

import com.ibpms.domain.enums.InstanceStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full status of a ProcessInstance, including visual timeline for the CLIENT app (RF-00b).
 */
public record ProcessStatusResponse(
        String processInstanceId,
        String currentNodeId,
        String currentNodeLabel,
        String currentDepartmentId,
        String currentDepartmentName,
        InstanceStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String clientId,
        String policyName,
        List<NodeProgressItem> nodeProgress
) {}
