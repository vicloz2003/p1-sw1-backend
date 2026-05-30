package com.ibpms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Shared {@link RestClient} for the Python ML/DL microservice (ibpms_ml, :8001), used by
 * both the intelligent agent (RF-2) and the risk engine (RF-3).
 *
 * <p>Two Spring Boot 4 / Framework 7 pitfalls are handled here so every ML integration is
 * immune to them:
 * <ul>
 *   <li><b>HTTP transport:</b> the default {@code JdkClientHttpRequestFactory} negotiates
 *       HTTP/2, which uvicorn (h11, HTTP/1.1) rejects — the request body is dropped and the
 *       service answers 422. {@link SimpleClientHttpRequestFactory} speaks clean HTTP/1.1.</li>
 *   <li><b>Converters:</b> a statically-built {@code RestClient.builder()} carries no
 *       {@code HttpMessageConverters}, so JSON/String/multipart bodies serialize empty. We
 *       register them explicitly (Jackson 3 for JSON, form + byte-array for audio upload).</li>
 * </ul>
 */
@Configuration
public class MlClientConfig {

    @Bean
    public RestClient mlRestClient(@Value("${ibpms.ml.url}") String mlBaseUrl) {
        return pythonClient(mlBaseUrl);
    }

    /** Client to ibpms_ia (:8000, Gemini) — dynamic report interpretation (RF-4). */
    @Bean
    public RestClient iaRestClient(@Value("${ibpms.ia.url}") String iaBaseUrl) {
        return pythonClient(iaBaseUrl);
    }

    private static RestClient pythonClient(String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new SimpleClientHttpRequestFactory())
                .messageConverters(converters -> {
                    converters.add(new JacksonJsonHttpMessageConverter());
                    converters.add(new StringHttpMessageConverter());
                    converters.add(new ByteArrayHttpMessageConverter());
                    converters.add(new AllEncompassingFormHttpMessageConverter());
                })
                .build();
    }
}
