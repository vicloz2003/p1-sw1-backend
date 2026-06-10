package com.ibpms.dto.response;

/**
 * Nodo con mayor tiempo promedio de ejecución de tareas.
 * averageDurationHours = promedio de (completedAt - assignedAt) en horas (2 decimales)
 * para todas las ActivityTask COMPLETED de ese nodo.
 */
public record BottleneckResponse(
        String nodeId,
        String nodeLabel,
        double averageDurationHours
) {}

