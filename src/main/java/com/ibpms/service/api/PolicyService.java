package com.ibpms.service.api;

import com.ibpms.domain.DocumentRequirement;
import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;

import java.util.List;

public interface PolicyService {
    List<PolicyResponse> getAll();
    PolicyResponse create(CreatePolicyRequest request, String userId);
    PolicyResponse update(String id, UpdatePolicyRequest request);
    PolicyResponse publish(String id);
    List<PolicyResponse> getActive();
    PolicyResponse getById(String id);
    void deletePolicy(String id);

    // ── Document requirement management (RF-01) ───────────────────────────────
    PolicyResponse addDocumentRequirement(String policyId, DocumentRequirement requirement);
    PolicyResponse updateDocumentRequirement(String policyId, String reqId,
                                             DocumentRequirement requirement);
    PolicyResponse removeDocumentRequirement(String policyId, String reqId);

    // ── Semantic tags for NLP classification (RF-11) ─────────────────────────
    PolicyResponse updateTags(String policyId, List<String> tags);
}

