package com.ibpms.dto.response;

/**
 * Nodo con mayor tiempo promedio de ejecución de tareas.
 * averageDurationSeconds = promedio de (completedAt - assignedAt) en segundos
 * para todas las ActivityTask COMPLETED de ese nodo.
 */
public record BottleneckResponse(
        String nodeId,
        String nodeLabel,
        double averageDurationSeconds
) {}

