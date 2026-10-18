package com.mdm.web;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="mdm.demo.enabled",havingValue="true")
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;
    public DemoData(JdbcTemplate jdbc,PasswordEncoder encoder,@Value("${MDM_DEMO_PASSWORD:}") String password) {
        this.jdbc=jdbc;this.encoder=encoder;this.password=password;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if(password.length()<12) throw new IllegalStateException("Demo mode requires MDM_DEMO_PASSWORD with at least 12 characters");
        for(String team:new String[]{"MDM Demo Operations","MDM Demo Engineering"}) {
            jdbc.update("INSERT INTO teams(name) VALUES (?) ON DUPLICATE KEY UPDATE name=name",team);
        }
        long ops=jdbc.queryForObject("SELECT id FROM teams WHERE name='MDM Demo Operations'",Long.class);
        long engineering=jdbc.queryForObject("SELECT id FROM teams WHERE name='MDM Demo Engineering'",Long.class);
        addUser("MDM Admin","admin@mdm.local","ADMIN",ops);
        long alice=addUser("Alice Morgan","alice@mdm.local","MEMBER",ops);
        addUser("Ben Carter","ben@mdm.local","MEMBER",ops);
        addUser("Eva Chen","eva@mdm.local","MEMBER",engineering);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM work_items WHERE team_id=?",Integer.class,ops)==0) {
            jdbc.update("INSERT INTO work_items(title,description,priority,team_id,created_by,next_action) VALUES (?,?,?,?,?,?)",
                "Investigate delayed customer payment","A customer reports a transfer has not arrived. Verify the transaction before contacting finance.","URGENT",ops,alice,"Check the payment provider reference");
            jdbc.update("INSERT INTO work_items(title,description,priority,team_id,created_by,next_action) VALUES (?,?,?,?,?,?)",
                "Review onboarding handoff","Support and operations need a shared handoff checklist.","MEDIUM",ops,alice,"Draft the checklist with support");
            jdbc.update("INSERT INTO activity_logs(work_item_id,actor_id,action,details) SELECT id,?,'CREATED','Demo work item created' FROM work_items WHERE team_id=?",alice,ops);
        }
    }
    private long addUser(String name,String email,String role,long team) {
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE email=email",name,email,encoder.encode(password),role);
        long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        jdbc.update("INSERT INTO team_memberships(user_id,team_id) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=user_id",id,team);
        return id;
    }
}
