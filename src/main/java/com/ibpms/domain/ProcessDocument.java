package com.ibpms.domain;

import com.ibpms.domain.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a document file associated with a specific process instance
 * (RF-02, RF-03, RF-04). Stored in the {@code documents} collection.
 *
 * <p>The corresponding S3 key follows the scheme:
 * {@code /{policyId}/{processInstanceId}/{requirementId}/{uuid}_{filename}}
 */
@Document("documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDocument {

    @Id
    private String id;

    private String processInstanceId;

    private String businessPolicyId;

    /**
     * The {@link DocumentRequirement#getId()} this file satisfies.
     * {@code null} when it is an ad-hoc document attached at an ACTION node.
     */
    private String documentRequirementId;

    private String fileName;

    private String mimeType;

    /** S3 object key used to generate presigned URLs. */
    private String s3Key;

    /** userId of the uploader. */
    private String uploadedBy;

    /** Role of the uploader: CLIENT or EMPLOYEE. */
    private String uploadedByRole;

    private DocumentStatus status;

    /** Previous versions, populated when the document is replaced (RF-07). */
    private List<DocumentVersion> versions;

    private DocumentPermissions permissions;

    private LocalDateTime uploadedAt;

    private LocalDateTime confirmedAt;

    /**
     * If the document was attached during an ACTION node completion,
     * this is the {@code ActivityTask} id.
     */
    private String taskId;

    /**
     * URL of an active collaborative editing session (Google Docs / OnlyOffice).
     * Null when no session has been opened (RF-09).
     */
    private String collaborativeSessionUrl;
}
