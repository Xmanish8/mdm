package com.mdm;

import com.mdm.web.WorkService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="mdm.demo.enabled=false")
@EnabledIfEnvironmentVariable(named="MDM_DB_TESTS",matches="true")
class DatabaseBehaviourTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkService service;
    long team,aditi,rahul,item;
    @BeforeEach void setup() {
        String tag=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO teams(name) VALUES (?)","MDM test "+tag);
        team=jdbc.queryForObject("SELECT id FROM teams WHERE name=?",Long.class,"MDM test "+tag);
        aditi=user("aditi-"+tag+"@test.invalid");rahul=user("rahul-"+tag+"@test.invalid");
        jdbc.update("INSERT INTO team_memberships(user_id,team_id) VALUES (?,?),(?,?)",aditi,team,rahul,team);
        item=service.create(actor(aditi),"Concurrency test","Test fixture","MEDIUM",team,"Investigate");
    }
    long user(String email) {
        jdbc.update("INSERT INTO users(name,email,password_hash) VALUES ('MDM test',?,'not-a-login-hash')",email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email=?",Long.class,email);
    }
    WorkService.Actor actor(long id) { return new WorkService.Actor(id,"Test",false); }
    @AfterEach void cleanup() {
        if(team!=0) {
            jdbc.update("DELETE FROM operation_requests WHERE work_item_id IN (SELECT id FROM work_items WHERE team_id=?)",team);
            jdbc.update("DELETE FROM activity_logs WHERE work_item_id IN (SELECT id FROM work_items WHERE team_id=?)",team);
            jdbc.update("DELETE FROM work_items WHERE team_id=?",team);
        }
        if(team!=0) {jdbc.update("DELETE FROM team_memberships WHERE team_id=?",team);jdbc.update("DELETE FROM teams WHERE id=?",team);}
        if(aditi!=0) jdbc.update("DELETE FROM users WHERE id=?",aditi);
        if(rahul!=0) jdbc.update("DELETE FROM users WHERE id=?",rahul);
    }
    @Test void simultaneousClaimsHaveOneOwnerAndOneEvent() throws Exception {
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long id:new long[]{aditi,rahul}) results.add(pool.submit(()->{
                gate.await();try {service.claim(actor(id),item,0);return true;}
                catch(ResponseStatusException e) {assertEquals(409,e.getStatusCode().value());return false;}
            }));
            gate.countDown();int successes=0;for(var f:results) if(f.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
            assertEquals(1,jdbc.queryForObject("SELECT version FROM work_items WHERE id=?",Integer.class,item));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM activity_logs WHERE work_item_id=? AND action='CLAIMED'",Integer.class,item));
        } finally {pool.shutdownNow();pool.awaitTermination(15,TimeUnit.SECONDS);}
    }
    @Test void staleEditPreservesNewerWork() {
        service.claim(actor(aditi),item,0);
        service.update(actor(aditi),item,1,"BLOCKED","HIGH","Await provider response");
        var e=assertThrows(ResponseStatusException.class,()->service.update(actor(aditi),item,1,"PENDING_APPROVAL","LOW","Old draft"));
        assertEquals(409,e.getStatusCode().value());
        assertEquals("Await provider response",jdbc.queryForObject("SELECT next_action FROM work_items WHERE id=?",String.class,item));
    }
    @Test void outsiderCannotReadWork() {
        var outsider=new WorkService.Actor(-1,"Outsider",false);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.item(outsider,item)).getStatusCode().value());
    }
    @Test void repeatedCreateAndCommentAreAppliedOnce() {
        String token=UUID.randomUUID().toString();
        long created=service.create(actor(aditi),"Duplicate test","Context","HIGH",team,"Check",token);
        assertEquals(created,service.create(actor(aditi),"Duplicate test","Context","HIGH",team,"Check",token));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM work_items WHERE team_id=? AND title='Duplicate test'",Integer.class,team));
        String commentToken=UUID.randomUUID().toString();
        service.comment(actor(aditi),created,"Update",commentToken);service.comment(actor(aditi),created,"Update",commentToken);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM activity_logs WHERE work_item_id=? AND action='COMMENT'",Integer.class,created));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.create(actor(aditi),"Changed","Context","HIGH",team,"Check",token)).getStatusCode().value());
    }
    @Test void reassignedWorkCanOnlyBeEditedByNewOwner() {
        service.claim(actor(aditi),item,0);service.reassign(actor(aditi),item,1,rahul,"Shift handoff");
        assertEquals(rahul,jdbc.queryForObject("SELECT assigned_to FROM work_items WHERE id=?",Long.class,item));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.update(actor(aditi),item,2,"BLOCKED","HIGH","Wait")).getStatusCode().value());
        service.update(actor(rahul),item,2,"BLOCKED","HIGH","Wait");
    }
}
