package com.ibpms.dto.request;

import com.ibpms.domain.ActivityNode;
import com.ibpms.domain.ActivityPartition;
import com.ibpms.domain.ControlFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePolicyRequest(
        @NotBlank String name,
        String description,
        @NotNull List<ActivityPartition> partitions,
        @NotNull List<ActivityNode> nodes,
        @NotNull List<ControlFlow> flows,
        String bpmnXml
) {}

