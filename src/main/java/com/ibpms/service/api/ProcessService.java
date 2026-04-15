package com.ibpms.service.api;

import com.ibpms.dto.request.StartProcessRequest;
import com.ibpms.dto.response.ProcessStatusResponse;

public interface ProcessService {
    ProcessStatusResponse startProcess(StartProcessRequest request, String userId);
    ProcessStatusResponse getStatus(String processInstanceId);
}

