package com.mdm.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Public review credentials are available only against a separate sample database. */
@Component
public class ReviewerAccess {
    public record Profile(String id,String name,String email,String role,String team,List<String> memberships) {
        public String roleLabel(){return role.equals("ADMIN")?"Administrator":"Member";}
        public String firstName(){return name.split(" ")[0];}
    }
    public record View(List<Profile> profiles,String password) {}
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final Environment environment;
    private View view;

    public ReviewerAccess(JdbcTemplate jdbc,PasswordEncoder passwords,Environment environment){
        this.jdbc=jdbc;this.passwords=passwords;this.environment=environment;
    }
    public static List<Profile> catalog(){
        Properties data=new Properties();
        try(var input=new ClassPathResource("reviewer-profiles.properties").getInputStream()){data.load(input);}
        catch(IOException e){throw new IllegalStateException("Reviewer profile definitions are unavailable",e);}
        return Arrays.stream(data.getProperty("profiles").split(",")).map(id->new Profile(id,
            data.getProperty(id+".name"),data.getProperty(id+".email"),data.getProperty(id+".role"),
            data.getProperty(id+".team"),List.of(data.getProperty(id+".memberships").split("\\|")))).toList();
    }
    @PostConstruct void validate(){
        if(!environment.getProperty("mdm.reviewer.enabled",Boolean.class,false))return;
        String password=environment.getProperty("mdm.reviewer.password","");
        require(password.length()>=12&&password.getBytes(StandardCharsets.UTF_8).length<=72,
            "Reviewer mode needs its separate public reviewer.password (12+ characters)");
        require("mdm_review_app".equals(environment.getProperty("spring.datasource.username")),
            "Reviewer mode requires the dedicated mdm_review_app database account");
        require("mdm_review".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)),
            "Public reviewer access requires the isolated mdm_review database");
        require(jdbc.queryForList("SELECT marker FROM reviewer_workspace WHERE id=1",String.class).equals(List.of("MDM_PUBLIC_REVIEW_ONLY")),
            "Reviewer workspace must be initialized as a fresh sample-only database");
        List<Profile> profiles=catalog();
        for(Profile profile:profiles){
            var rows=jdbc.queryForList("SELECT role,password_hash FROM users WHERE email=?",profile.email());
            require(rows.size()==1,"Import reviewer-bootstrap.sql before enabling reviewer mode");
            var row=rows.get(0);
            require(profile.role().equals(row.get("role"))&&passwords.matches(password,(String)row.get("password_hash")),
                "Reviewer account roles/passwords must match the prepared review configuration");
            for(String team:profile.memberships()){
                Integer membership=jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships m JOIN users u ON u.id=m.user_id JOIN teams t ON t.id=m.team_id WHERE u.email=? AND t.name=?",Integer.class,profile.email(),team);
                require(membership!=null&&membership==1,"Reviewer team memberships are incomplete");
            }
        }
        view=new View(profiles,password);
    }
    public View view(){return view;}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
