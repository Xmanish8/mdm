
package com.mdm.repository;

import com.mdm.model.ActivityLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ActivityLogRepository {

    private final JdbcTemplate jdbc;

    public ActivityLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
            long workItemId,
            long actorId,
            String action,
            String details) {

        String sql = """
            INSERT INTO activity_logs
            (work_item_id, actor_id, action, details)
            VALUES (?, ?, ?, ?)
            """;

        jdbc.update(
            sql,
            workItemId,
            actorId,
            action,
            details
        );
    }

    public List<ActivityLog> findByWorkItemId(
            long workItemId) {

        String sql = """
            SELECT *
            FROM activity_logs
            WHERE work_item_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 50
            """;

        return jdbc.query(sql, (rs, rowNum) ->
            new ActivityLog(
                rs.getLong("id"),
                rs.getLong("work_item_id"),
                rs.getLong("actor_id"),
                rs.getString("action"),
                rs.getString("details"),
                rs.getTimestamp("created_at")
                  .toLocalDateTime()
            ), workItemId
        );
    }
}
