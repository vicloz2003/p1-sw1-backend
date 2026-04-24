package com.ibpms.dto.request;

public record ColaboracionPayload(
        String sessionId,
        String policyId,
        String bpmnXml
) {}
