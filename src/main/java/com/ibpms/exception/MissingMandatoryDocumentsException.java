package com.ibpms.exception;

import java.util.List;

/**
 * Thrown when a mandatory PROCESS_START document requirement is not satisfied
 * before starting a process (HTTP 422 Unprocessable Entity).
 */
public class MissingMandatoryDocumentsException extends RuntimeException {

    private final List<String> missingDocumentNames;

    public MissingMandatoryDocumentsException(List<String> missingDocumentNames) {
        super("Missing " + missingDocumentNames.size() + " mandatory document(s) required at process start");
        this.missingDocumentNames = missingDocumentNames;
    }

    public List<String> getMissingDocumentNames() {
        return missingDocumentNames;
    }
}
