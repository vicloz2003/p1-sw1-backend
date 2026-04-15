package com.ibpms.service.api;

import com.ibpms.dto.response.BottleneckResponse;

import java.util.List;

public interface AnalyticsService {
    /**
     * Returns nodes ordered by average task duration (desc).
     * TODO: DEUDA TÉCNICA — reemplazar el cálculo in-memory por
     * un @Aggregation en ActivityTaskRepository para producción.
     */
    List<BottleneckResponse> getBottlenecks();
}

