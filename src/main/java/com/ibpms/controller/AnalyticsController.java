package com.ibpms.controller;

import com.ibpms.dto.response.BottleneckResponse;
import com.ibpms.service.api.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAuthority('ADMIN_DESIGNER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/bottlenecks")
    public ResponseEntity<List<BottleneckResponse>> getBottlenecks() {
        return ResponseEntity.ok(analyticsService.getBottlenecks());
    }
}

