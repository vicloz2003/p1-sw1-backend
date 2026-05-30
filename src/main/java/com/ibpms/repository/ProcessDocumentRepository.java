package com.ibpms.repository;

import com.ibpms.domain.ProcessDocument;
import com.ibpms.domain.enums.DocumentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcessDocumentRepository extends MongoRepository<ProcessDocument, String> {

    /** All documents for a given process instance (RF-04). */
    List<ProcessDocument> findByProcessInstanceId(String processInstanceId);

    /** All documents for a given process instance that are not DELETED. */
    List<ProcessDocument> findByProcessInstanceIdAndStatusNot(String processInstanceId,
                                                               DocumentStatus status);

    /** Documents linked to a specific task (RF-03). */
    List<ProcessDocument> findByTaskId(String taskId);

    /** Find pre-process documents by their IDs and status (used during startProcess validation). */
    List<ProcessDocument> findByIdInAndStatus(List<String> ids, DocumentStatus status);

    /** All non-deleted documents of a client across all their trámites (RF-1.4). */
    List<ProcessDocument> findByClientIdAndStatusNot(String clientId, DocumentStatus status);

    /** Non-deleted documents of a client within a specific policy (RF-1.4 — jefe query). */
    List<ProcessDocument> findByBusinessPolicyIdAndClientIdAndStatusNot(String businessPolicyId,
                                                                        String clientId,
                                                                        DocumentStatus status);
}
