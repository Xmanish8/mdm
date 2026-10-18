package com.mdm;

import com.mdm.web.DemoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:demo_profiles;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=never","mdm.demo.enabled=false"})
class DemoDataTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100),email VARCHAR(150) UNIQUE,password_hash VARCHAR(255),role VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS teams(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100) UNIQUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS team_memberships(user_id BIGINT REFERENCES users(id),team_id BIGINT REFERENCES teams(id),PRIMARY KEY(user_id,team_id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS work_items(id BIGINT AUTO_INCREMENT PRIMARY KEY,title VARCHAR(160),description TEXT,priority VARCHAR(20),team_id BIGINT REFERENCES teams(id),created_by BIGINT REFERENCES users(id),next_action VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS activity_logs(work_item_id BIGINT REFERENCES work_items(id),actor_id BIGINT REFERENCES users(id),action VARCHAR(50),details VARCHAR(500))");
        for(String table:new String[]{"activity_logs","work_items","team_memberships","teams","users"})jdbc.update("DELETE FROM "+table);
    }
    void seed(String password) { new DemoData(jdbc,passwords,password).run(new DefaultApplicationArguments()); }
    long user(String name,String email,String hash,String role) {
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",name,email,hash,role);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    @Test void freshSetupCreatesIndianProfilesAndManishAdmin() {
        seed("test-only-demo-password");
        assertEquals("Manish Marathe",jdbc.queryForObject("SELECT name FROM users WHERE email='manish@mdm.local'",String.class));
        assertEquals("ADMIN",jdbc.queryForObject("SELECT role FROM users WHERE email='manish@mdm.local'",String.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE name IN ('Aditi Sharma','Rahul Deshmukh','Sneha Patil') AND role='MEMBER'",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM work_items",Integer.class));
    }
    @Test void legacyProfileRenamePreservesPasswordIdentityAndWork() {
        String oldHash=passwords.encode("test-only-original-password");
        long id=user("MDM Admin","admin@mdm.local",oldHash,"ADMIN");
        jdbc.update("INSERT INTO teams(name) VALUES ('Existing team')");
        long team=jdbc.queryForObject("SELECT id FROM teams WHERE name='Existing team'",Long.class);
        jdbc.update("INSERT INTO team_memberships VALUES (?,?)",id,team);
        jdbc.update("INSERT INTO work_items(title,team_id,created_by) VALUES ('Keep this work',?,?)",team,id);
        seed("test-only-new-demo-password");
        assertEquals(id,jdbc.queryForObject("SELECT id FROM users WHERE email='manish@mdm.local'",Long.class));
        assertEquals(oldHash,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id));
        assertEquals(id,jdbc.queryForObject("SELECT created_by FROM work_items WHERE title='Keep this work'",Long.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships WHERE user_id=? AND team_id=?",Integer.class,id,team));
    }
    @Test void repeatedStartupKeepsExistingPasswordsAndDoesNotDuplicateData() {
        seed("test-only-first-password");
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE email='manish@mdm.local'",String.class);
        seed("test-only-second-password");
        assertEquals(hash,jdbc.queryForObject("SELECT password_hash FROM users WHERE email='manish@mdm.local'",String.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM work_items",Integer.class));
    }
    @Test void customProfilesAndConflictingDestinationAreNotOverwritten() {
        long custom=user("My existing profile","alice@mdm.local","keep-custom-hash","MEMBER");
        long legacy=user("MDM Admin","admin@mdm.local","keep-legacy-hash","ADMIN");
        long destination=user("Existing Manish","manish@mdm.local","keep-destination-hash","MEMBER");
        seed("test-only-demo-password");
        assertEquals("alice@mdm.local",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,custom));
        assertEquals("admin@mdm.local",jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,legacy));
        assertEquals("keep-destination-hash",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,destination));
        assertEquals("MEMBER",jdbc.queryForObject("SELECT role FROM users WHERE id=?",String.class,destination));
    }
}
