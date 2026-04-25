package com.ibpms.service.impl;

import com.ibpms.domain.ProcessInstance;
import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.ProcessStatusResponse;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.ProcessService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class ProcessServiceImpl implements ProcessService {

    private final WorkflowEngine workflowEngine;
    private final ProcessInstanceRepository processInstanceRepository;
    private final BusinessPolicyRepository policyRepository;

    public ProcessServiceImpl(WorkflowEngine workflowEngine,
                              ProcessInstanceRepository processInstanceRepository,
                              BusinessPolicyRepository policyRepository) {
        this.workflowEngine = workflowEngine;
        this.processInstanceRepository = processInstanceRepository;
        this.policyRepository = policyRepository;
    }

    @Override
    public ProcessStatusResponse startProcess(StartProcessRequest request, String userId) {
        ProcessInstance instance = workflowEngine.startProcess(
                request.policyId(),
                userId,
                request.initialData() != null ? request.initialData() : Collections.emptyMap()
        );
        instance.setClientId(request.clientId());
        processInstanceRepository.save(instance);
        return toStatusResponse(instance);
    }

    @Override
    public ProcessStatusResponse getStatus(String processInstanceId) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new ProcessInstanceNotFoundException(
                        "Process instance not found: " + processInstanceId));
        return toStatusResponse(instance);
    }

    private ProcessStatusResponse toStatusResponse(ProcessInstance instance) {
        String policyName = "";
        String currentNodeLabel = instance.getCurrentNodeId();

        var policy = policyRepository.findById(instance.getBusinessPolicyId());
        if (policy.isPresent()) {
            policyName = policy.get().getName();
            currentNodeLabel = policy.get().getNodes().stream()
                    .filter(n -> n.getId().equals(instance.getCurrentNodeId()))
                    .map(n -> n.getLabel())
                    .findFirst()
                    .orElse(instance.getCurrentNodeId());
        }
        return new ProcessStatusResponse(
                instance.getId(),
                instance.getCurrentNodeId(),
                currentNodeLabel,
                instance.getStatus(),
                instance.getStartedAt(),
                instance.getClientId(),
                policyName
        );
    }

    @Override
    public List<ProcessStatusResponse> getByClientId(String clientId) {
        return processInstanceRepository
                .findByClientId(clientId)
                .stream()
                .map(this::toStatusResponse)
                .toList();
    }
}

