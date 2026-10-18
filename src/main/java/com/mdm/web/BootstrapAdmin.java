package com.mdm.web;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
@Component @ConditionalOnProperty(name="mdm.bootstrap.enabled",havingValue="true")
public class BootstrapAdmin implements ApplicationRunner {
    private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final String email,password;
    public BootstrapAdmin(JdbcTemplate jdbc,PasswordEncoder encoder,@Value("${MDM_BOOTSTRAP_EMAIL:}") String email,@Value("${bootstrap.password:}") String password){this.jdbc=jdbc;this.encoder=encoder;this.email=email;this.password=password;}
    @Override @Transactional public void run(ApplicationArguments args){
        if(email.length()>150||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||email.endsWith(".invalid"))throw new IllegalStateException("Set a real bootstrap admin email");
        if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalStateException("Initial admin password must be 12+ characters and at most 72 UTF-8 bytes");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,email)!=0)throw new IllegalStateException("Account already exists; bootstrap will not overwrite it");
        jdbc.update("INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,'ADMIN')","Workspace administrator",email.toLowerCase(java.util.Locale.ROOT),encoder.encode(password));
    }
}
