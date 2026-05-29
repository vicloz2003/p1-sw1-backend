package com.ibpms.repository;

import com.ibpms.domain.DocumentAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentAuditLogRepository extends MongoRepository<DocumentAuditLog, String> {

    /** Full audit trail for a specific document, ordered by timestamp ascending. */
    List<DocumentAuditLog> findByDocumentIdOrderByTimestampAsc(String documentId);

    /** All audit entries for an entire process instance. */
    List<DocumentAuditLog> findByProcessInstanceIdOrderByTimestampAsc(String processInstanceId);
}
