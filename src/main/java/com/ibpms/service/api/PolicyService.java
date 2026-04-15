package com.ibpms.service.api;

import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;

import java.util.List;

public interface PolicyService {
    List<PolicyResponse> getAll();
    PolicyResponse create(CreatePolicyRequest request, String userId);
    PolicyResponse update(String id, UpdatePolicyRequest request);
    PolicyResponse publish(String id);
}

