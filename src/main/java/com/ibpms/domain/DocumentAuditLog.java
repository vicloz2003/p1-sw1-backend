package com.ibpms.domain;

import com.ibpms.domain.enums.DocumentAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Append-only audit trail for every operation performed on a document (RF-08).
 * Stored in the {@code document_audit_logs} collection.
 *
 * <p>No DELETE endpoint exists for this collection — see RNF-05.
 */
@Document("document_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAuditLog {

    @Id
    private String id;

    private String documentId;

    private String processInstanceId;

    private String userId;

    private String userRole;

    private DocumentAction action;

    private LocalDateTime timestamp;

    /** Source IP address from the HTTP request. */
    private String ipAddress;

    /** Optional extra context, e.g. the target version or the reason for refusal. */
    private String detail;
}
