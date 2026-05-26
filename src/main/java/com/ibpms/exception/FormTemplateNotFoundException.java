package com.ibpms.exception;

public class FormTemplateNotFoundException extends RuntimeException {
    public FormTemplateNotFoundException(String id) {
        super("Form template not found: " + id);
    }
}
