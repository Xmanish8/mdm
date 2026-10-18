
package com.mdm.repository;

import com.mdm.model.WorkItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class WorkItemRepository {

    private final JdbcTemplate jdbc;

    public WorkItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<WorkItem> MAPPER =
        (rs, rowNum) -> new WorkItem(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("priority"),
            rs.getString("status"),
            rs.getLong("team_id"),
            rs.getObject("assigned_to") == null
                ? null : rs.getLong("assigned_to"),
            rs.getLong("created_by"),
            rs.getString("next_action"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );

    // 1. Create a new work item
    public long create(
            String title,
            String description,
            String priority,
            long teamId,
            long createdBy,
            String nextAction) {

        String sql = """
            INSERT INTO work_items
            (title, description, priority, status,
             team_id, created_by, next_action, version)
            VALUES (?, ?, ?, 'OPEN', ?, ?, ?, 0)
            """;

        KeyHolder keys = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps =
                connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS
                );

            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, priority);
            ps.setLong(4, teamId);
            ps.setLong(5, createdBy);
            ps.setString(6, nextAction);

            return ps;
        }, keys);

        return Objects.requireNonNull(
            keys.getKey()
        ).longValue();
    }

    // 2. Find a work item by ID
    public Optional<WorkItem> findById(long id) {
        String sql = """
            SELECT * FROM work_items
            WHERE id = ?
            """;

        return jdbc.query(
            sql, MAPPER, id
        ).stream().findFirst();
    }

    // 3. Paginated list with team visibility
    public List<WorkItem> findVisible(
            long userId,
            boolean admin,
            int limit,
            int offset) {

        String sql = """
            SELECT w.*
            FROM work_items w
            WHERE (? = 1 OR EXISTS (
                SELECT 1
                FROM team_memberships tm
                WHERE tm.team_id = w.team_id
                  AND tm.user_id = ?
            ))
            ORDER BY w.updated_at DESC, w.id DESC
            LIMIT ? OFFSET ?
            """;

        return jdbc.query(
            sql,
            MAPPER,
            admin ? 1 : 0,
            userId,
            Math.max(1, Math.min(limit, 50)),
            Math.max(0, offset)
        );
    }

    // 4. Atomically claim a work item
    public int claim(long id, long userId) {
        String sql = """
            UPDATE work_items w
            SET w.assigned_to = ?,
                w.status = 'IN_PROGRESS',
                w.version = w.version + 1
            WHERE w.id = ?
              AND w.status = 'OPEN'
              AND w.assigned_to IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM team_memberships tm
                  WHERE tm.team_id = w.team_id
                    AND tm.user_id = ?
              )
            """;

        return jdbc.update(
            sql, userId, id, userId
        );
    }

    // 5. Update with optimistic concurrency control
    public int updateStatus(
            long id,
            String status,
            String nextAction,
            int expectedVersion) {

        String sql = """
            UPDATE work_items
            SET status = ?,
                next_action = ?,
                version = version + 1
            WHERE id = ?
              AND version = ?
            """;

        return jdbc.update(
            sql,
            status,
            nextAction,
            id,
            expectedVersion
        );
    }
}
