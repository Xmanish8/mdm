
package com.mdm.model;

import java.time.LocalDateTime;

public record WorkItem(
        Long id,
        String title,
        String description,
        String priority,
        String status,
        Long teamId,
        Long assignedTo,
        Long createdBy,
        String nextAction,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
