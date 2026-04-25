package com.ibpms.dto.response;

import com.ibpms.domain.enums.InstanceStatus;

import java.time.LocalDateTime;

public record ProcessStatusResponse(
        String processInstanceId,
        String currentNodeId,
        String currentNodeLabel,
        InstanceStatus status,
        LocalDateTime startedAt,
        String clientId,
        String policyName
) {}

