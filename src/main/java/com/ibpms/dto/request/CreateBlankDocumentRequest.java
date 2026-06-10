package com.ibpms.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * RF-1.10: a functionary creates a blank Office document at the node they are working,
 * to fill it in collaboratively with their department.
 *
 * <p>{@code kind} selects the template: {@code WORD} (.docx), {@code CELL} (.xlsx) or
 * {@code SLIDE} (.pptx). {@code taskId}/{@code nodeId} anchor the document to the node so the
 * authorization check can confirm the caller's department is the one working that node.
 */
public record CreateBlankDocumentRequest(
        @NotBlank String processInstanceId,
        String taskId,
        String nodeId,
        @NotBlank String fileName,
        @NotBlank String kind
) {}
