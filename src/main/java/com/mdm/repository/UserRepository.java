
package com.mdm.repository;

import com.mdm.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> findByEmail(String email) {
        String sql = """
            SELECT id, name, email, password_hash, role
            FROM users
            WHERE email = ?
            """;

        return jdbc.query(sql, (rs, rowNum) ->
                new User(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    rs.getString("role")
                ), email
        ).stream().findFirst();
    }

    public boolean belongsToTeam(long userId, long teamId) {
        String sql = """
            SELECT COUNT(*)
            FROM team_memberships
            WHERE user_id = ? AND team_id = ?
            """;

        Integer count = jdbc.queryForObject(
                sql, Integer.class, userId, teamId
        );

        return count != null && count > 0;
    }
}
