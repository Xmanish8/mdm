
package com.mdm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="MDM_DB_TESTS", matches="true")
@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void verifyDatabaseTables() {

        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                  'users',
                  'teams',
                  'team_memberships',
                  'work_items',
                  'activity_logs'
              )
            """, Integer.class);

        assertEquals(5, count);
    }
}
