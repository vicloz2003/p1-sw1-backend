package com.ibpms.service.impl;

import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.PolicyStatus;
import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;
import com.ibpms.exception.PolicyInUseException;
import com.ibpms.exception.PolicyNotFoundException;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.PolicyService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PolicyServiceImpl implements PolicyService {

    private final BusinessPolicyRepository policyRepository;
    private final ProcessInstanceRepository processInstanceRepository;

    public PolicyServiceImpl(BusinessPolicyRepository policyRepository,
                             ProcessInstanceRepository processInstanceRepository) {
        this.policyRepository = policyRepository;
        this.processInstanceRepository = processInstanceRepository;
    }

    @Override
    public List<PolicyResponse> getAll() {
        return policyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }
    @Override
    public PolicyResponse getById(String id) {
        BusinessPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        return toResponse(policy);
    }

    @Override
    public List<PolicyResponse> getActive() {
        return policyRepository.findByStatus(PolicyStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PolicyResponse create(CreatePolicyRequest request, String userId) {
        BusinessPolicy policy = new BusinessPolicy();
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setCreatedBy(userId);
        policy.setStatus(PolicyStatus.DRAFT);
        policy.setPartitions(request.partitions());
        policy.setNodes(request.nodes());
        policy.setFlows(request.flows());
        policy.setBpmnXml(request.bpmnXml());
        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    public PolicyResponse update(String id, UpdatePolicyRequest request) {
        BusinessPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        if (processInstanceRepository.existsByBusinessPolicyIdAndStatus(id, InstanceStatus.ACTIVE)) {
            throw new PolicyInUseException("Policy has active instances and cannot be edited.");
        }
        policy.setName(request.name());
        policy.setDescription(request.description());
        policy.setPartitions(request.partitions());
        policy.setNodes(request.nodes());
        policy.setFlows(request.flows());
        policy.setBpmnXml(request.bpmnXml());
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    public PolicyResponse publish(String id) {
        BusinessPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    private PolicyResponse toResponse(BusinessPolicy p) {
        return new PolicyResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCreatedBy(),
                p.getStatus(), p.getPartitions(), p.getNodes(), p.getFlows(),
                p.getCreatedAt(), p.getUpdatedAt(),
                p.getBpmnXml()
        );
    }
}

