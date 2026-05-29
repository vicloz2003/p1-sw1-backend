package com.ibpms.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents one historical version of a {@link ProcessDocument}.
 * A new version is appended each time a document is replaced (RF-07).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion {
    private String versionId;
    private String s3Key;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private Long sizeBytes;
}
