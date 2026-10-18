package com.mdm.web;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    public AdminService(JdbcTemplate jdbc,PasswordEncoder passwords){this.jdbc=jdbc;this.passwords=passwords;}
    public void requireAdmin(WorkService.Actor a){if(!a.admin())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access is required");}
    public List<Map<String,Object>> users(WorkService.Actor a,int page){requireAdmin(a);return jdbc.queryForList("SELECT id,name,email,role FROM users ORDER BY id LIMIT 51 OFFSET ?",(long)Math.max(0,Math.min(page,10000))*50);}
    public List<Map<String,Object>> members(WorkService.Actor a,long team,int page){requireAdmin(a);return jdbc.queryForList("SELECT u.id,u.name,u.email FROM users u JOIN team_memberships m ON m.user_id=u.id WHERE m.team_id=? ORDER BY u.id LIMIT 51 OFFSET ?",team,(long)Math.max(0,Math.min(page,10000))*50);}
    public List<Map<String,Object>> history(WorkService.Actor a){requireAdmin(a);return jdbc.queryForList("SELECT l.*,u.name AS actor_name FROM admin_activity l JOIN users u ON u.id=l.actor_id ORDER BY l.id DESC LIMIT 50");}
    public List<Map<String,Object>> analytics(WorkService.Actor a){requireAdmin(a);return jdbc.queryForList("SELECT page,day,views FROM analytics_daily WHERE day>=DATE_SUB(CURRENT_DATE,INTERVAL 30 DAY) ORDER BY day DESC,page LIMIT 100");}
    private void audit(WorkService.Actor a,String action,String detail){jdbc.update("INSERT INTO admin_activity(actor_id,action,details) VALUES (?,?,?)",a.id(),action,detail);}
    private void check(boolean valid,String reason){if(!valid)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
    @Transactional public void createTeam(WorkService.Actor a,String name){
        requireAdmin(a);check(name!=null&&!name.isBlank()&&name.length()<=100,"Team name must contain 1–100 characters");
        jdbc.update("INSERT INTO teams(name) VALUES (?)",name.trim());audit(a,"TEAM_CREATED",name.trim());
    }
    @Transactional public void createUser(WorkService.Actor a,String name,String email,String password){
        requireAdmin(a);check(name!=null&&!name.isBlank()&&name.length()<=100,"Name must contain 1–100 characters");
        check(email!=null&&email.length()<=150&&email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),"Enter a valid email address");
        check(password!=null&&password.length()>=12&&password.getBytes(StandardCharsets.UTF_8).length<=72,"Password must be at least 12 characters and at most 72 UTF-8 bytes");
        jdbc.update("INSERT INTO users(name,email,password_hash) VALUES (?,?,?)",name.trim(),email.toLowerCase(Locale.ROOT),passwords.encode(password));
        audit(a,"USER_CREATED",email.toLowerCase(Locale.ROOT));
    }
    @Transactional public void addMember(WorkService.Actor a,long user,long team){
        requireAdmin(a);
        check(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Integer.class,user)==1,"User does not exist");
        check(jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE id=?",Integer.class,team)==1,"Team does not exist");
        jdbc.update("INSERT INTO team_memberships(user_id,team_id) VALUES (?,?)",user,team);
        audit(a,"MEMBER_ADDED","User "+user+" added to team "+team);
    }
}
