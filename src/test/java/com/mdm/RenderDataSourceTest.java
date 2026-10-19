package com.mdm;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Bind the deployed profile to a real pool, rather than mocking away datasource creation. */
class RenderDataSourceTest {
    @Test void renderConfigurationCreatesAUsablePoolBeforeAnyApplicationQuery() {
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class))
            .withPropertyValues("spring.profiles.active=prod,render",
                "spring.datasource.url=jdbc:h2:mem:render_pool;IGNORE_UNKNOWN_SETTINGS=TRUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa","spring.datasource.password=",
                "spring.datasource.hikari.maximum-pool-size=1","spring.datasource.hikari.minimum-idle=0")
            .run(context->{
                assertNull(context.getStartupFailure(),"Render datasource configuration must bind without starting/sealing the pool prematurely");
                HikariDataSource pool=context.getBean(HikariDataSource.class);
                assertFalse(pool.isRunning(),"Property binding must not start/seal the pool before the first query");
                assertEquals(60000,pool.getConnectionTimeout());
                assertEquals(60000,pool.getInitializationFailTimeout());
                assertEquals(1,context.getBean(JdbcTemplate.class).queryForObject("SELECT 1",Integer.class));
            });
    }
}
