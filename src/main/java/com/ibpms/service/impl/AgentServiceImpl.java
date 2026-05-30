package com.ibpms.service.impl;

import com.ibpms.domain.BusinessPolicy;
import com.ibpms.domain.DocumentRequirement;
import com.ibpms.domain.enums.PolicyStatus;
import com.ibpms.dto.response.AgentClassifyResponse;
import com.ibpms.dto.response.TranscribeResponse;
import com.ibpms.exception.AgentUnavailableException;
import com.ibpms.repository.BusinessPolicyRepository;
import com.ibpms.service.api.AgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentServiceImpl implements AgentService {

    private final BusinessPolicyRepository policyRepository;
    private final RestClient restClient;

    public AgentServiceImpl(BusinessPolicyRepository policyRepository,
                            @Value("${ibpms.ml.url}") String mlBaseUrl) {
        this.policyRepository = policyRepository;
        // ROOT CAUSE of the agent 503: Spring Boot 4 / Framework 7's default RestClient uses
        // the JDK HttpClient (JdkClientHttpRequestFactory), which negotiates HTTP/2. uvicorn
        // (h11, HTTP/1.1 only) rejected the upgrade ("Invalid HTTP request received") and the
        // request body was dropped (Content-Length: none, empty body) → ibpms_ml answered 422
        // "Field required". SimpleClientHttpRequestFactory uses HttpURLConnection (clean
        // HTTP/1.1 with a correct Content-Length), so the body reaches ibpms_ml intact.
        // We also register the message converters explicitly because the static
        // RestClient.builder() does not inherit the application's HttpMessageConverters.
        this.restClient = RestClient.builder()
                .baseUrl(mlBaseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .messageConverters(converters -> {
                    converters.add(new JacksonJsonHttpMessageConverter());
                    converters.add(new StringHttpMessageConverter());
                    converters.add(new ByteArrayHttpMessageConverter());
                    converters.add(new AllEncompassingFormHttpMessageConverter());
                })
                .build();
    }

    @Override
    public AgentClassifyResponse classify(String text) {
        // Load ACTIVE policies with their tags/description as classification criteria
        List<BusinessPolicy> activePolicies = policyRepository.findByStatus(PolicyStatus.ACTIVE);

        // Build the payload as plain Maps to guarantee correct JSON serialization.
        // Private Java records can be serialized as {} by some Jackson configurations;
        // Map<String,Object> is always serialized correctly (RF-2.3).
        List<Map<String, Object>> policiesPayload = activePolicies.stream()
                .map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",          p.getId()          != null ? p.getId()          : "");
                    m.put("name",        p.getName()        != null ? p.getName()        : "");
                    m.put("description", p.getDescription() != null ? p.getDescription() : "");
                    m.put("tags",        p.getTags()        != null ? p.getTags()        : List.of());
                    return m;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text",     text);
        payload.put("policies", policiesPayload);

        MlClassifyResponse ml;
        try {
            ml = restClient.post()
                    .uri("/agent/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(MlClassifyResponse.class);
        } catch (Exception e) {
            throw new AgentUnavailableException(
                    "No se pudo clasificar la solicitud (servicio de IA no disponible).", e);
        }
        if (ml == null) {
            throw new AgentUnavailableException("Respuesta vacía del servicio de IA.", null);
        }

        // Enrich with the mandatory PROCESS_START documents of the recommended policy (RF-2.5)
        List<DocumentRequirement> requiredDocs = List.of();
        if (ml.confident() && ml.policyId() != null) {
            requiredDocs = activePolicies.stream()
                    .filter(p -> p.getId().equals(ml.policyId()))
                    .findFirst()
                    .map(p -> p.getDocumentRequirements() == null ? List.<DocumentRequirement>of()
                            : p.getDocumentRequirements().stream()
                                    .filter(r -> "PROCESS_START".equals(r.getUploadStage()))
                                    .toList())
                    .orElse(List.of());
        }

        List<AgentClassifyResponse.PolicyMatch> alternatives = ml.alternatives() == null ? List.of()
                : ml.alternatives().stream()
                        .map(a -> new AgentClassifyResponse.PolicyMatch(a.policyId(), a.policyName(), a.score()))
                        .toList();

        return new AgentClassifyResponse(
                ml.policyId(), ml.policyName(), ml.confidence(), ml.confident(),
                alternatives, ml.message(), requiredDocs);
    }

    @Override
    public TranscribeResponse transcribe(MultipartFile audio) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        try {
            parts.add("file", new MultipartFileResource(audio));
        } catch (IOException e) {
            throw new AgentUnavailableException("No se pudo leer el archivo de audio.", e);
        }

        try {
            // Do NOT set Content-Type manually: Spring's FormHttpMessageConverter
            // must write it (including the multipart boundary) for Python to parse the body.
            return restClient.post()
                    .uri("/agent/transcribe")
                    .body(parts)
                    .retrieve()
                    .body(TranscribeResponse.class);
        } catch (Exception e) {
            throw new AgentUnavailableException(
                    "No se pudo transcribir el audio (servicio de IA no disponible).", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal DTOs (ibpms_ml response deserialization)
    // ─────────────────────────────────────────────────────────────────────────

    /** Raw classification response as returned by ibpms_ml (before backend enrichment). */
    private record MlClassifyResponse(
            String policyId, String policyName, double confidence, boolean confident,
            List<MlMatch> alternatives, String message) {}

    private record MlMatch(String policyId, String policyName, double score) {}

    /**
     * Wraps a MultipartFile as a Spring Resource that exposes a filename, so RestClient
     * can stream it as a multipart "file" part to ibpms_ml.
     */
    private static class MultipartFileResource
            extends org.springframework.core.io.ByteArrayResource {
        private final String filename;

        MultipartFileResource(MultipartFile file) throws IOException {
            super(file.getBytes());
            this.filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "audio.wav";
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
