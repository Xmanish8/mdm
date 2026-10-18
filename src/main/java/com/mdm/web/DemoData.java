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
        renameLegacyUser("admin@mdm.local","MDM Admin","ADMIN","manish@mdm.local","Manish Marathe");
        renameLegacyUser("alice@mdm.local","Alice Morgan","MEMBER","aditi@mdm.local","Aditi Sharma");
        renameLegacyUser("ben@mdm.local","Ben Carter","MEMBER","rahul@mdm.local","Rahul Deshmukh");
        renameLegacyUser("eva@mdm.local","Eva Chen","MEMBER","sneha@mdm.local","Sneha Patil");
        addUser("Manish Marathe","manish@mdm.local","ADMIN",ops);
        long aditi=addUser("Aditi Sharma","aditi@mdm.local","MEMBER",ops);
        addUser("Rahul Deshmukh","rahul@mdm.local","MEMBER",ops);
        addUser("Sneha Patil","sneha@mdm.local","MEMBER",engineering);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM work_items WHERE team_id=?",Integer.class,ops)==0) {
            jdbc.update("INSERT INTO work_items(title,description,priority,team_id,created_by,next_action) VALUES (?,?,?,?,?,?)",
                "Check a delayed UPI payment","A customer reports a UPI payment that has not reached the account. Check its status before raising a bank query.","URGENT",ops,aditi,"Verify the UPI transaction reference");
            jdbc.update("INSERT INTO work_items(title,description,priority,team_id,created_by,next_action) VALUES (?,?,?,?,?,?)",
                "Prepare the new-joiner checklist","Operations needs a checklist for account access, equipment and team introductions.","MEDIUM",ops,aditi,"Confirm the access requirements with HR");
            jdbc.update("INSERT INTO activity_logs(work_item_id,actor_id,action,details) SELECT id,?,'CREATED','Sample work item created' FROM work_items WHERE team_id=?",aditi,ops);
        }
    }
    private void renameLegacyUser(String oldEmail,String oldName,String role,String email,String name) {
        // Match only the original sample profile; never merge accounts or replace password hashes.
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)==0) {
            jdbc.update("UPDATE users SET name=?,email=? WHERE email=? AND name=? AND role=?",name,email,oldEmail,oldName,role);
        }
    }
    private long addUser(String name,String email,String role,long team) {
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE email=email",name,email,encoder.encode(password),role);
        long id=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
        jdbc.update("INSERT INTO team_memberships(user_id,team_id) VALUES (?,?) ON DUPLICATE KEY UPDATE user_id=user_id",id,team);
        return id;
    }
}
