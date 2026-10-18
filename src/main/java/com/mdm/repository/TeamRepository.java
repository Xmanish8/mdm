
package com.mdm.repository;

import com.mdm.model.Team;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TeamRepository {

    private final JdbcTemplate jdbc;

    public TeamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Team> findAll() {
        String sql = """
            SELECT id, name
            FROM teams
            ORDER BY name
            """;

        return jdbc.query(sql, (rs, rowNum) ->
                new Team(
                    rs.getLong("id"),
                    rs.getString("name")
                )
        );
    }
}
