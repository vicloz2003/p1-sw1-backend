package com.ibpms.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class S3Service {

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    // ── Legacy method (keeps S3Controller unchanged) ──────────────────────────

    /**
     * Original presigned PUT method used by the existing {@code S3Controller}.
     * Uses an auto-generated key under {@code uploads/}.
     */
    public Map<String, String> generatePresignedUrl(String fileName, String contentType) {
        String key = "uploads/" + UUID.randomUUID() + "/" + fileName;
        String presignedUrl = buildPresignedPutUrl(key, contentType, Duration.ofMinutes(10));
        String publicUrl = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;

        return Map.of(
                "uploadUrl", presignedUrl,
                "publicUrl", publicUrl,
                "key", key
        );
    }

    // ── Document management methods (RF-02, RF-06, RF-10) ────────────────────

    /**
     * Generates a presigned PUT URL for a structured document S3 key.
     * Key pattern: {@code /{policyId}/{processInstanceId}/{requirementId}/{uuid}_{filename}}
     *
     * @param policyId          business policy id
     * @param processInstanceId process instance id
     * @param requirementId     document requirement id (or "adhoc" for task documents)
     * @param fileName          original file name
     * @param mimeType          MIME type for the Content-Type header
     * @return the computed S3 key and presigned PUT URL (valid for 15 minutes)
     */
    public Map<String, String> initiateDocumentUpload(String policyId,
                                                       String clientId,
                                                       String processInstanceId,
                                                       String requirementId,
                                                       String fileName,
                                                       String mimeType) {
        // Repository organized per policy AND per client (RF-1.4):
        // {policyId}/{clientId}/{processInstanceId}/{requirementId}/{uuid}_{filename}
        // NOTE: no leading slash — a leading "/" yields a double-slash URL
        // (bucket.s3.amazonaws.com//key) that some HTTP clients (e.g. the OnlyOffice
        // downloader) normalize, breaking the SigV4 signature → S3 400.
        String clientSegment = (clientId != null && !clientId.isBlank()) ? clientId : "sin_cliente";
        String key = policyId
                + "/" + clientSegment
                + "/" + processInstanceId
                + "/" + requirementId
                + "/" + UUID.randomUUID() + "_" + sanitizeFileName(fileName);

        String presignedUrl = buildPresignedPutUrl(key, mimeType, Duration.ofMinutes(15));
        return Map.of("key", key, "presignedUrl", presignedUrl);
    }

    /**
     * Generates a presigned GET URL for secure download (RF-06).
     *
     * @param s3Key  the object key stored in {@link com.ibpms.domain.ProcessDocument}
     * @param expiry URL validity duration (typically 15 minutes)
     * @return the presigned download URL as a string
     */
    public String generatePresignedGetUrl(String s3Key, Duration expiry) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build()) {

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiry)
                    .getObjectRequest(getRequest)
                    .build();

            PresignedGetObjectRequest presignedGetRequest = presigner.presignGetObject(presignRequest);
            return presignedGetRequest.url().toString();
        }
    }

    /**
     * Checks that an S3 object actually exists (HeadObject). Used by the confirm
     * endpoint (RF-10) to validate the upload before marking the document CONFIRMED.
     *
     * @param s3Key the object key to probe
     * @return {@code true} if the object exists and is accessible
     */
    public boolean objectExists(String s3Key) {
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build()) {

            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the size in bytes of an S3 object, or {@code -1} if it does not exist.
     * Used to enforce {@code maxSizeBytes} at confirm time (RF-1.7).
     */
    public long getObjectSize(String s3Key) {
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build()) {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build()).contentLength();
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * Uploads raw bytes to S3 server-side (RF-1.10). Used by the OnlyOffice callback to
     * persist a document edited collaboratively in the Document Server.
     */
    public void putObject(String s3Key, byte[] content, String contentType) {
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build()) {

            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType(contentType != null ? contentType : "application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(content));
        }
    }

    /**
     * Downloads an object's bytes server-side (RF-1.10). Used by the OnlyOffice content
     * endpoint so the Document Server fetches the file through the backend instead of a
     * presigned S3 URL.
     */
    public byte[] getObject(String s3Key) {
        try (S3Client s3Client = S3Client.builder()
                .region(Region.of(region))
                .build()) {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build()).asByteArray();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildPresignedPutUrl(String key, String contentType, Duration duration) {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(region))
                .build()) {

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .putObjectRequest(objectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            return presignedRequest.url().toString();
        }
    }

    /** Removes path traversal characters from the original file name. */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
