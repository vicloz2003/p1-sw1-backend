package com.ibpms.service.api;

import com.ibpms.dto.response.AgentClassifyResponse;
import com.ibpms.dto.response.TranscribeResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Intelligent agent (RF-2): replaces the human attention desk. Classifies the
 * client's request into a business policy and transcribes voice to text.
 * Orchestrates the ibpms_ml Python microservice.
 */
public interface AgentService {

    /**
     * Classifies the client's free-text request into the most suitable ACTIVE policy.
     * Loads active policies (with tags/description) and delegates to ibpms_ml.
     */
    AgentClassifyResponse classify(String text);

    /** Transcribes an uploaded audio file to text (proxy to ibpms_ml / Whisper). */
    TranscribeResponse transcribe(MultipartFile audio);
}
