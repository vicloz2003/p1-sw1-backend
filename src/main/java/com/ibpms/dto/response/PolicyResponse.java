package com.ibpms.dto.response;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ControlFlow;
import com.ibpms.domain.enums.PolicyStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PolicyResponse(
        String id,
        String name,
        String description,
        String createdBy,
        PolicyStatus status,
        List<ActivityPartition> partitions,
        List<ActivityNode> nodes,
        List<ControlFlow> flows,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String bpmnXml
) {}

