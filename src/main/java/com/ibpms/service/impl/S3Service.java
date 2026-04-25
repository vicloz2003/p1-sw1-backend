package com.ibpms.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    public Map<String, String> generatePresignedUrl(String fileName, String contentType) {
        String key = "uploads/" + UUID.randomUUID() + "/" + fileName;

        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build();

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);

        String presignedUrl = presignedRequest.url().toString();
        String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        presigner.close();

        return Map.of(
                "uploadUrl", presignedUrl,
                "publicUrl", publicUrl,
                "key", key
        );
    }
}
