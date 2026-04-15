package com.ibpms.service.impl;

import com.ibpms.domain.ProcessInstance;
import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.ProcessStatusResponse;
import com.ibpms.engine.api.WorkflowEngine;
import com.ibpms.exception.ProcessInstanceNotFoundException;
import com.ibpms.repository.ProcessInstanceRepository;
import com.ibpms.service.api.ProcessService;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class ProcessServiceImpl implements ProcessService {

    private final WorkflowEngine workflowEngine;
    private final ProcessInstanceRepository processInstanceRepository;

    public ProcessServiceImpl(WorkflowEngine workflowEngine,
                              ProcessInstanceRepository processInstanceRepository) {
        this.workflowEngine = workflowEngine;
        this.processInstanceRepository = processInstanceRepository;
    }

    @Override
    public ProcessStatusResponse startProcess(StartProcessRequest request, String userId) {
        ProcessInstance instance = workflowEngine.startProcess(
                request.policyId(),
                userId,
                request.initialData() != null ? request.initialData() : Collections.emptyMap()
        );
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
        return new ProcessStatusResponse(
                instance.getId(),
                instance.getCurrentNodeId(),
                instance.getStatus(),
                instance.getStartedAt()
        );
    }
}

