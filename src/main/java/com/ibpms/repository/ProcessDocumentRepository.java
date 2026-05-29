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
}
