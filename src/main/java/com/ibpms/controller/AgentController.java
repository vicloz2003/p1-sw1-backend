package com.ibpms.controller;

import com.ibpms.dto.request.AgentClassifyRequest;
import com.ibpms.dto.response.AgentClassifyResponse;
import com.ibpms.dto.response.TranscribeResponse;
import com.ibpms.service.api.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Intelligent agent endpoints (RF-2). Available to the end user (CLIENT) and staff.
 * Acts as the gateway/orchestrator to the ibpms_ml Python microservice.
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * RF-2.1: classify the client's free-text request into a business policy.
     */
    @PostMapping("/classify")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<AgentClassifyResponse> classify(
            @Valid @RequestBody AgentClassifyRequest request) {
        return ResponseEntity.ok(agentService.classify(request.text()));
    }

    /**
     * RF-2.2: transcribe a recorded audio clip to text (Whisper via ibpms_ml).
     */
    @PostMapping(value = "/transcribe", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority('CLIENT', 'EMPLOYEE', 'ADMIN_DESIGNER')")
    public ResponseEntity<TranscribeResponse> transcribe(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(agentService.transcribe(file));
    }
}
