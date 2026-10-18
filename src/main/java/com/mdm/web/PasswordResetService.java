package com.mdm.web;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {
    private final JdbcTemplate jdbc;private final PasswordEncoder passwords;private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    public PasswordResetService(JdbcTemplate jdbc,PasswordEncoder passwords,Clock clock){this.jdbc=jdbc;this.passwords=passwords;this.clock=clock;}
    public record Issued(String email,String token){@Override public String toString(){return "Issued[redacted]";}}
    static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    static boolean tokenFormat(String token){return token!=null&&token.matches("[A-Za-z0-9_-]{43}");}
    public static void validatePassword(String password,String confirmation){
        if(password==null||password.isBlank()||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 12 characters and at most 72 UTF-8 bytes for your new password.");
        if(!password.equals(confirmation))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The two passwords do not match.");
    }
    @Transactional public Optional<Issued> issue(String email){
        var users=jdbc.queryForList("SELECT id,email FROM users WHERE email=? FOR UPDATE",email);
        if(users.isEmpty())return Optional.empty();
        long id=((Number)users.getFirst().get("id")).longValue();
        byte[] bytes=new byte[32];random.nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",id);
        jdbc.update("INSERT INTO password_reset_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",digest(token),id,Timestamp.from(clock.instant().plus(Duration.ofMinutes(15))));
        return Optional.of(new Issued(users.getFirst().get("email").toString(),token));
    }
    public boolean usable(String token){
        if(!tokenFormat(token))return false;
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE token_hash=? AND expires_at>?",Integer.class,digest(token),Timestamp.from(clock.instant()));
        return count!=null&&count==1;
    }
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.GONE,"This reset link is invalid, expired or already used. Request a new link.");}
    @Transactional public String reset(String token,String password,String confirmation){
        validatePassword(password,confirmation);if(!tokenFormat(token))throw invalid();String hash=digest(token);
        var tokenRows=jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash=?",hash);
        if(tokenRows.isEmpty())throw invalid();long id=((Number)tokenRows.getFirst().get("user_id")).longValue();
        // All issue/reset operations lock the user first, then recheck the token under that lock.
        var users=jdbc.queryForList("SELECT id,email FROM users WHERE id=? FOR UPDATE",id);
        if(users.isEmpty())throw invalid();
        var valid=jdbc.queryForList("SELECT token_hash FROM password_reset_tokens WHERE token_hash=? AND user_id=? AND expires_at>? FOR UPDATE",hash,id,Timestamp.from(clock.instant()));
        if(valid.isEmpty())throw invalid();
        if(jdbc.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(password),id)!=1)throw invalid();
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?",id);
        jdbc.update("INSERT INTO admin_activity(actor_id,action,details) VALUES (?,'PASSWORD_RESET','Password changed using a single-use email reset link')",id);
        return users.getFirst().get("email").toString();
    }
    public void revoke(String token){if(tokenFormat(token))jdbc.update("DELETE FROM password_reset_tokens WHERE token_hash=?",digest(token));}
}
