package com.ibpms.controller;

import com.ibpms.service.impl.S3Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/s3")
public class S3Controller {

    private final S3Service s3Service;

    public S3Controller(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    @GetMapping("/presigned-url")
    @PreAuthorize("hasAnyAuthority('ADMIN_DESIGNER', 'EMPLOYEE')")
    public ResponseEntity<Map<String, String>> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam String contentType) {
        return ResponseEntity.ok(
                s3Service.generatePresignedUrl(fileName, contentType));
    }
}
