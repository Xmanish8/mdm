package com.mdm.web;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration @EnableScheduling
public class Maintenance {
    private final JdbcTemplate jdbc;
    public Maintenance(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Scheduled(fixedDelay=3600000,initialDelay=3600000) public void expire(){
        jdbc.update("DELETE FROM auth_rate_limits WHERE window_start<? LIMIT 10000",System.currentTimeMillis()/1000-86400);
        jdbc.update("DELETE FROM analytics_daily WHERE day<DATE_SUB(CURRENT_DATE,INTERVAL 30 DAY) LIMIT 10000");
        jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at<CURRENT_TIMESTAMP LIMIT 10000");
    }
}
