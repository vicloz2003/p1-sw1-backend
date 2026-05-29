package com.ibpms.service.impl;

import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.enums.InstanceStatus;
import com.ibpms.domain.enums.PolicyStatus;
import com.ibpms.dto.request.CreatePolicyRequest;
import com.ibpms.dto.request.UpdatePolicyRequest;
import com.ibpms.dto.response.PolicyResponse;
import com.ibpms.engine.validator.DiagramValidator;
import com.ibpms.exception.PolicyInUseException;
import com.ibpms.exception.PolicyNotFoundException;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.PolicyService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PolicyServiceImpl implements PolicyService {

    private final BusinessPolicyRepository policyRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final DiagramValidator diagramValidator;

    public PolicyServiceImpl(BusinessPolicyRepository policyRepository,
                             ProcessInstanceRepository processInstanceRepository,
                             DiagramValidator diagramValidator) {
        this.policyRepository = policyRepository;
        this.processInstanceRepository = processInstanceRepository;
        this.diagramValidator = diagramValidator;
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
        if (policyRepository.existsByName(request.name())) {
            throw new RuntimeException("Ya existe una política con ese nombre: " + request.name());
        }
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
        // Validate diagram integrity before publishing (DT-09)
        diagramValidator.validate(policy);
        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    public void deletePolicy(String id) {
        BusinessPolicy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        if (processInstanceRepository.existsByBusinessPolicyIdAndStatus(id, InstanceStatus.ACTIVE)) {
            throw new PolicyInUseException("No se puede eliminar una política con trámites en proceso.");
        }
        policyRepository.deleteById(id);
    }

    // ── Document requirement management (RF-01) ───────────────────────────────

    @Override
    public PolicyResponse addDocumentRequirement(String policyId, DocumentRequirement requirement) {
        BusinessPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));
        if (requirement.getId() == null || requirement.getId().isBlank()) {
            requirement.setId(UUID.randomUUID().toString());
        }
        if (policy.getDocumentRequirements() == null) {
            policy.setDocumentRequirements(new ArrayList<>());
        }
        policy.getDocumentRequirements().add(requirement);
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    public PolicyResponse updateDocumentRequirement(String policyId, String reqId,
                                                     DocumentRequirement requirement) {
        BusinessPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));
        if (policy.getDocumentRequirements() == null) {
            throw new IllegalArgumentException("No document requirements found for policy: " + policyId);
        }
        boolean replaced = false;
        List<DocumentRequirement> updated = new ArrayList<>();
        for (DocumentRequirement dr : policy.getDocumentRequirements()) {
            if (dr.getId().equals(reqId)) {
                requirement.setId(reqId);
                updated.add(requirement);
                replaced = true;
            } else {
                updated.add(dr);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException(
                    "Document requirement not found: " + reqId + " in policy: " + policyId);
        }
        policy.setDocumentRequirements(updated);
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    @Override
    public PolicyResponse removeDocumentRequirement(String policyId, String reqId) {
        BusinessPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));
        if (processInstanceRepository.existsByBusinessPolicyIdAndStatus(policyId, InstanceStatus.ACTIVE)) {
            throw new PolicyInUseException(
                    "Cannot modify document requirements of a policy with active instances.");
        }
        if (policy.getDocumentRequirements() != null) {
            policy.getDocumentRequirements().removeIf(dr -> dr.getId().equals(reqId));
        }
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    // ── NLP Tags (RF-11) ──────────────────────────────────────────────────────

    @Override
    public PolicyResponse updateTags(String policyId, List<String> tags) {
        BusinessPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyId));
        policy.setTags(tags);
        policy.setUpdatedAt(LocalDateTime.now());
        return toResponse(policyRepository.save(policy));
    }

    private PolicyResponse toResponse(BusinessPolicy p) {
        return new PolicyResponse(
                p.getId(), p.getName(), p.getDescription(), p.getCreatedBy(),
                p.getStatus(), p.getPartitions(), p.getNodes(), p.getFlows(),
                p.getCreatedAt(), p.getUpdatedAt(),
                p.getBpmnXml(),
                p.getDocumentRequirements(),
                p.getTags()
        );
    }
}

