package com.mdm.model;

import java.time.LocalDateTime;

public record ActivityLog(
        Long id,
        Long workItemId,
        Long actorId,
        String action,
        String details,
        LocalDateTime createdAt
) {
}
