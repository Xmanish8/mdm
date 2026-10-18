package com.mdm;
import com.mdm.web.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Real transactions/row locks against isolated H2; actual MySQL tests remain opt-in. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:password_reset_tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=never","mdm.demo.enabled=false","mdm.password-reset.email-enabled=false"})
class PasswordResetTransactionTest {
    @Autowired JdbcTemplate jdbc;@Autowired PasswordResetService resets;@Autowired PasswordEncoder passwords;
    long user;String original;
    String email="reset-transaction@test.invalid";
    @BeforeEach void fixture(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS users(id BIGINT AUTO_INCREMENT PRIMARY KEY,email VARCHAR(150) NOT NULL UNIQUE,password_hash VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS password_reset_tokens(token_hash CHAR(64) PRIMARY KEY,user_id BIGINT NOT NULL UNIQUE,expires_at TIMESTAMP NOT NULL,FOREIGN KEY(user_id) REFERENCES users(id))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS admin_activity(id BIGINT AUTO_INCREMENT PRIMARY KEY,actor_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,details VARCHAR(500) NOT NULL,FOREIGN KEY(actor_id) REFERENCES users(id))");
        jdbc.update("DELETE FROM password_reset_tokens");jdbc.update("DELETE FROM admin_activity");jdbc.update("DELETE FROM users");
        original=passwords.encode("test-only-old-password");jdbc.update("INSERT INTO users(email,password_hash) VALUES (?,?)",email,original);user=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    @Test void expiredTokenCannotChangePassword(){var reset=resets.issue(email).orElseThrow();jdbc.update("UPDATE password_reset_tokens SET expires_at=? WHERE user_id=?",Timestamp.from(Instant.now().minusSeconds(60)),user);assertFalse(resets.usable(reset.token()));assertThrows(ResponseStatusException.class,()->resets.reset(reset.token(),"test-only-new-password","test-only-new-password"));assertEquals(original,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user));}
    @Test void newestLinkReplacesOldAndCanOnlyBeUsedOnce(){var first=resets.issue(email).orElseThrow();var second=resets.issue(email).orElseThrow();assertFalse(resets.usable(first.token()));assertTrue(resets.usable(second.token()));resets.reset(second.token(),"test-only-new-password","test-only-new-password");assertFalse(resets.usable(second.token()));assertThrows(ResponseStatusException.class,()->resets.reset(second.token(),"test-only-new-password","test-only-new-password"));assertTrue(passwords.matches("test-only-new-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user)));}
    @Test void failedAuditRollsBackPasswordAndTokenConsumption(){
        var issued=resets.issue(email).orElseThrow();jdbc.execute("ALTER TABLE admin_activity ADD CONSTRAINT test_only_reject_reset CHECK(action <> 'PASSWORD_RESET')");
        try{assertThrows(org.springframework.dao.DataAccessException.class,()->resets.reset(issued.token(),"test-only-new-password","test-only-new-password"));assertEquals(original,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user));assertTrue(resets.usable(issued.token()));}
        finally{jdbc.execute("ALTER TABLE admin_activity DROP CONSTRAINT test_only_reject_reset");}
    }
    @Test void concurrentUseCommitsExactlyOneChange()throws Exception{
        String token=resets.issue(email).orElseThrow().token();var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<Boolean>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{gate.await();try{resets.reset(token,"test-only-new-password","test-only-new-password");return true;}catch(ResponseStatusException expired){assertEquals(410,expired.getStatusCode().value());return false;}}));gate.countDown();int successes=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))successes++;assertEquals(1,successes);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_activity WHERE actor_id=?",Integer.class,user));assertFalse(resets.usable(token));}
        finally{pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);}
    }
}
