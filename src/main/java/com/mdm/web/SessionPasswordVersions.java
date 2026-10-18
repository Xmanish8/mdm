package com.mdm.web;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
@Service
public class SessionPasswordVersions {
    private static final String KEY="mdm.auth.password-version";
    private final JdbcTemplate jdbc;
    public SessionPasswordVersions(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public static final class VersionedUser extends User {
        private final String version;
        public VersionedUser(String email,String hash,String role){super(email,hash,List.of(new SimpleGrantedAuthority("ROLE_"+role)));this.version=PasswordResetService.digest(hash);}
        public String version(){return version;}
    }
    public void record(HttpSession session,String version){session.setAttribute(KEY,version);}
    public boolean matches(HttpSession session,String email){
        if(session==null)return false;Object saved;try{saved=session.getAttribute(KEY);}catch(IllegalStateException expired){return false;}
        if(!(saved instanceof String version))return false;
        var rows=jdbc.queryForList("SELECT password_hash FROM users WHERE email=?",email);if(rows.size()!=1)return false;
        String current=PasswordResetService.digest(rows.getFirst().get("password_hash").toString());
        return MessageDigest.isEqual(version.getBytes(StandardCharsets.US_ASCII),current.getBytes(StandardCharsets.US_ASCII));
    }
}
