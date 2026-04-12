package com.ibpms.engine.api;

import com.ibpms.domain.ProcessInstance;

import java.util.Map;

public interface WorkflowEngine {
    ProcessInstance startProcess(String policyId, String userId, Map<String, Object> initialData);
    void completeTask(String taskId, Map<String, Object> formData, String userId);
}

