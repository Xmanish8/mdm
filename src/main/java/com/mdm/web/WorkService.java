package com.mdm.web;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Statement;

@Service
public class WorkService {
    private final JdbcTemplate jdbc;
    public WorkService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Actor(long id,String name,boolean admin) {}
    public Actor actor(String email) {
        return jdbc.query("SELECT id,name,role FROM users WHERE email=?",(r,n)->
            new Actor(r.getLong(1),r.getString(2),"ADMIN".equals(r.getString(3))),email)
            .stream().findFirst().orElseThrow(()->error(HttpStatus.FORBIDDEN,"Account unavailable"));
    }
    private ResponseStatusException error(HttpStatus status,String text) { return new ResponseStatusException(status,text); }
    public void requireTeam(Actor actor,long team) {
        Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE id=?",Integer.class,team);
        if(exists==null || exists==0) throw error(HttpStatus.NOT_FOUND,"Team not found");
        if(!actor.admin() && jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships WHERE user_id=? AND team_id=?",Integer.class,actor.id(),team)==0)
            throw error(HttpStatus.FORBIDDEN,"You do not belong to this team");
    }
    public List<Map<String,Object>> teams(Actor a) {
        return jdbc.queryForList("SELECT t.* FROM teams t WHERE ? OR EXISTS (SELECT 1 FROM team_memberships m WHERE m.team_id=t.id AND m.user_id=?) ORDER BY t.name",a.admin(),a.id());
    }
    public Map<String,Long> summary(Actor a) {
        Map<String,Long> result=new LinkedHashMap<>();
        for(String s:List.of("OPEN","IN_PROGRESS","BLOCKED","PENDING_APPROVAL","RESOLVED","CANCELLED")) result.put(s,0L);
        jdbc.queryForList("SELECT w.status,COUNT(*) AS total FROM work_items w WHERE ? OR EXISTS (SELECT 1 FROM team_memberships m WHERE m.team_id=w.team_id AND m.user_id=?) GROUP BY w.status",a.admin(),a.id())
            .forEach(r->result.put(r.get("status").toString(),((Number)r.get("total")).longValue()));
        return result;
    }
    public List<String> nextStates(Map<String,Object> item,Actor a) {
        String current=item.get("status").toString();
        return List.of("OPEN","IN_PROGRESS","BLOCKED","PENDING_APPROVAL","RESOLVED","CANCELLED").stream()
            .filter(s->s.equals(current)||allowedTransition(current,s,a.admin())).toList();
    }
    public List<Map<String,Object>> candidates(Actor a,long id) {
        var w=item(a,id);
        return jdbc.queryForList("SELECT u.id,u.name FROM users u JOIN team_memberships m ON m.user_id=u.id WHERE m.team_id=? ORDER BY u.name,u.id",w.get("team_id"));
    }
    @Transactional public void reassign(Actor a,long id,int version,long owner,String reason) {
        var w=item(a,id);var old=(Number)w.get("assigned_to");
        if(!a.admin() && (old==null||old.longValue()!=a.id())) throw error(HttpStatus.FORBIDDEN,"Only the owner or an admin may transfer responsibility");
        String state=w.get("status").toString();
        if(!Set.of("IN_PROGRESS","BLOCKED").contains(state)) throw error(HttpStatus.BAD_REQUEST,"Only active or blocked work can be reassigned");
        if(old!=null&&old.longValue()==owner) throw error(HttpStatus.BAD_REQUEST,"Choose a different owner");
        text(reason,1,255,"Transfer reason");
        int n=jdbc.update("UPDATE work_items SET assigned_to=?,version=version+1 WHERE id=? AND version=? AND EXISTS (SELECT 1 FROM team_memberships m WHERE m.team_id=work_items.team_id AND m.user_id=?)",owner,id,version,owner);
        if(n!=1) throw error(HttpStatus.CONFLICT,"The item changed or the selected user is not a team member. Refresh and retry.");
        log(id,a,"REASSIGNED","Owner "+old+" → "+owner+"; "+reason.trim());
    }
    private String fingerprint(String... values) {
        try {
            var payload=new StringBuilder();for(String v:values)payload.append(v.length()).append(':').append(v);
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    private Long lockRequest(Actor a,String token,String kind,String hash) {
        try { if(token==null || !UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException e){throw error(HttpStatus.BAD_REQUEST,"Invalid request token. Refresh the form.");}
        jdbc.update("INSERT INTO operation_requests(actor_id,request_key,kind,fingerprint) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE request_key=request_key",a.id(),token,kind,hash);
        var row=jdbc.queryForMap("SELECT kind,fingerprint,work_item_id,completed FROM operation_requests WHERE actor_id=? AND request_key=? FOR UPDATE",a.id(),token);
        if(!kind.equals(row.get("kind"))||!hash.equals(row.get("fingerprint"))) throw error(HttpStatus.CONFLICT,"This form was already submitted with different data. Refresh before creating another request.");
        Object completed=row.get("completed");
        boolean done=Boolean.TRUE.equals(completed)||(completed instanceof Number n&&n.intValue()!=0);
        return done?((Number)row.get("work_item_id")).longValue():null;
    }
    private void completeRequest(Actor a,String token,long id) {
        jdbc.update("UPDATE operation_requests SET completed=TRUE,work_item_id=? WHERE actor_id=? AND request_key=?",id,a.id(),token);
    }
    public Map<String,Object> item(Actor a,long id) {
        var rows=jdbc.queryForList("SELECT w.*,t.name AS team_name,u.name AS owner_name FROM work_items w JOIN teams t ON t.id=w.team_id LEFT JOIN users u ON u.id=w.assigned_to WHERE w.id=?",id);
        if(rows.isEmpty()) throw error(HttpStatus.NOT_FOUND,"Work item not found");
        var w=rows.getFirst(); requireTeam(a,((Number)w.get("team_id")).longValue()); return w;
    }
    public List<Map<String,Object>> list(Actor a,String q,String status,String priority,long team,int page) {
        if(q.length()>100) throw error(HttpStatus.BAD_REQUEST,"Search must be at most 100 characters");
        String search="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return jdbc.queryForList("""
            SELECT w.*,t.name AS team_name,u.name AS owner_name FROM work_items w
            JOIN teams t ON t.id=w.team_id LEFT JOIN users u ON u.id=w.assigned_to
            WHERE (? OR EXISTS (SELECT 1 FROM team_memberships m WHERE m.team_id=w.team_id AND m.user_id=?))
            AND (?='' OR w.title LIKE ? ESCAPE '!' OR w.id=?) AND (?='' OR w.status=?)
            AND (?='' OR w.priority=?) AND (?=0 OR w.team_id=?)
            ORDER BY w.updated_at DESC,w.id DESC LIMIT 21 OFFSET ?
            """,a.admin(),a.id(),q,search,q.matches("[0-9]{1,15}")?Long.parseLong(q):-1,status,status,priority,priority,team,team,(long)Math.max(0,Math.min(page,10000))*20);
    }
    public List<Map<String,Object>> history(Actor a,long id,long before) {
        item(a,id);
        return jdbc.queryForList("SELECT l.*,u.name AS actor_name FROM activity_logs l JOIN users u ON u.id=l.actor_id WHERE l.work_item_id=? AND l.id<? ORDER BY l.id DESC LIMIT 50",id,before);
    }
    private void log(long id,Actor a,String action,String details) {
        jdbc.update("INSERT INTO activity_logs(work_item_id,actor_id,action,details) VALUES (?,?,?,?)",id,a.id(),action,details);
    }
    private void text(String v,int min,int max,String field) {
        if(v==null || v.trim().length()<min || v.length()>max || v.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')) throw error(HttpStatus.BAD_REQUEST,field+" must contain "+min+"–"+max+" characters without unsupported control characters");
    }
    @Transactional public long create(Actor a,String title,String description,String priority,long team,String next) {
        return create(a,title,description,priority,team,next,UUID.randomUUID().toString());
    }
    @Transactional public long create(Actor a,String title,String description,String priority,long team,String next,String token) {
        requireTeam(a,team); text(title,1,160,"Title");text(description,0,10000,"Description");text(next,1,255,"Next action");
        if(!Set.of("LOW","MEDIUM","HIGH","URGENT").contains(priority)) throw error(HttpStatus.BAD_REQUEST,"Invalid priority");
        Long previous=lockRequest(a,token,"CREATE",fingerprint(title,description,priority,Long.toString(team),next));
        if(previous!=null)return previous;
        var key=new GeneratedKeyHolder();
        jdbc.update(c->{var ps=c.prepareStatement("INSERT INTO work_items(title,description,priority,team_id,created_by,next_action) VALUES (?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,title.trim()); ps.setString(2,description);ps.setString(3,priority);ps.setLong(4,team);ps.setLong(5,a.id());ps.setString(6,next.trim());return ps;},key);
        long id=Objects.requireNonNull(key.getKey()).longValue();log(id,a,"CREATED","Created with priority "+priority);completeRequest(a,token,id);return id;
    }
    @Transactional public void claim(Actor a,long id,int version) {
        item(a,id);
        int n=jdbc.update("UPDATE work_items SET assigned_to=?,status='IN_PROGRESS',version=version+1 WHERE id=? AND version=? AND assigned_to IS NULL AND status='OPEN'",a.id(),id,version);
        if(n!=1) throw error(HttpStatus.CONFLICT,"Someone changed or claimed this item. Refresh to see the latest version.");
        log(id,a,"CLAIMED","Responsibility taken by "+a.name());
    }
    public static boolean allowedTransition(String from,String to,boolean admin) {
        return switch(from) {
            case "OPEN" -> to.equals("CANCELLED");
            case "IN_PROGRESS" -> Set.of("BLOCKED","PENDING_APPROVAL","CANCELLED").contains(to);
            case "BLOCKED" -> Set.of("IN_PROGRESS","CANCELLED").contains(to);
            case "PENDING_APPROVAL" -> admin && Set.of("RESOLVED","IN_PROGRESS").contains(to);
            default -> false;
        };
    }
    @Transactional public void update(Actor a,long id,int version,String status,String priority,String next) {
        var w=item(a,id); var owner=(Number)w.get("assigned_to");
        if(!a.admin() && (owner==null || owner.longValue()!=a.id())) throw error(HttpStatus.FORBIDDEN,"Only the owner or an admin may edit this item");
        String from=w.get("status").toString();
        if(!from.equals(status) && !allowedTransition(from,status,a.admin())) throw error(HttpStatus.BAD_REQUEST,"This workflow transition is not allowed");
        if(Set.of("RESOLVED","CANCELLED").contains(from)) throw error(HttpStatus.BAD_REQUEST,"Completed items cannot be edited");
        if(!Set.of("LOW","MEDIUM","HIGH","URGENT").contains(priority)) throw error(HttpStatus.BAD_REQUEST,"Invalid priority");
        text(next,1,255,"Next action");
        int n=jdbc.update("UPDATE work_items SET status=?,priority=?,next_action=?,version=version+1 WHERE id=? AND version=?",status,priority,next,id,version);
        if(n!=1) throw error(HttpStatus.CONFLICT,"Your copy is stale. Refresh and review the latest changes before trying again.");
        log(id,a,"UPDATED",from+" → "+status+"; priority "+w.get("priority")+" → "+priority+"; next: "+next);
    }
    @Transactional public void comment(Actor a,long id,String body) {
        comment(a,id,body,UUID.randomUUID().toString());
    }
    @Transactional public void comment(Actor a,long id,String body,String token) {
        item(a,id);text(body,1,500,"Comment");
        if(lockRequest(a,token,"COMMENT",fingerprint(Long.toString(id),body))!=null)return;
        log(id,a,"COMMENT",body.trim());completeRequest(a,token,id);
    }
}
