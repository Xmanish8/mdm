package com.mdm;
import com.mdm.web.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"mdm.demo.enabled=false","mdm.password-reset.email-enabled=false"})
@EnabledIfEnvironmentVariable(named="MDM_DB_TESTS",matches="true")
class PasswordResetDatabaseTest {
    @Autowired JdbcTemplate jdbc;@Autowired PasswordResetService resets;@Autowired PasswordEncoder passwords;
    long user;String email,original;
    @BeforeEach void fixture(){email="reset-"+UUID.randomUUID()+"@test.invalid";original=passwords.encode("test-only-old-password");jdbc.update("INSERT INTO users(name,email,password_hash) VALUES ('Reset test',?,?)",email,original);user=jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);}
    @AfterEach void clean(){if(user!=0){jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",user);jdbc.update("DELETE FROM admin_activity WHERE actor_id=?",user);jdbc.update("DELETE FROM users WHERE id=?",user);}}
    @Test void newestLinkWinsAndSuccessfulUseCannotRepeat(){
        var old=resets.issue(email).orElseThrow();var latest=resets.issue(email).orElseThrow();assertFalse(resets.usable(old.token()));assertTrue(resets.usable(latest.token()));assertTrue(resets.usable(latest.token()));
        resets.reset(latest.token(),"test-only-new-password","test-only-new-password");assertThrows(ResponseStatusException.class,()->resets.reset(latest.token(),"test-only-other-password","test-only-other-password"));
        assertTrue(passwords.matches("test-only-new-password",jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user)));
    }
    @Test void expiredTokenCannotUpdateAccount(){var issued=resets.issue(email).orElseThrow();jdbc.update("UPDATE password_reset_tokens SET expires_at=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE) WHERE user_id=?",user);assertFalse(resets.usable(issued.token()));assertThrows(ResponseStatusException.class,()->resets.reset(issued.token(),"test-only-new-password","test-only-new-password"));assertEquals(original,jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,user));}
    @Test void competingRedemptionsOnlyCommitOnce()throws Exception{
        var token=resets.issue(email).orElseThrow().token();var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{gate.await();try{resets.reset(token,"test-only-new-password","test-only-new-password");return true;}catch(ResponseStatusException expired){assertEquals(410,expired.getStatusCode().value());return false;}}));
            gate.countDown();int success=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_activity WHERE actor_id=? AND action='PASSWORD_RESET'",Integer.class,user));
        }finally{pool.shutdownNow();pool.awaitTermination(20,TimeUnit.SECONDS);}
    }
}
