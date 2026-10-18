package com.mdm.web;
import java.time.*;
import java.util.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordResetServiceTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);PasswordEncoder encoder=mock(PasswordEncoder.class);
    Clock clock=Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"),ZoneOffset.UTC);
    PasswordResetService service=new PasswordResetService(jdbc,encoder,clock);
    String token="A".repeat(43),password="test-only-new-password";
    @Test void issueStoresOnlyDigestWithFifteenMinuteExpiry(){
        when(jdbc.queryForList("SELECT id,email FROM users WHERE email=? FOR UPDATE","member@example.com")).thenReturn(List.of(Map.of("id",5L,"email","member@example.com")));
        var issued=service.issue("member@example.com").orElseThrow();assertTrue(issued.token().matches("[A-Za-z0-9_-]{43}"));
        verify(jdbc).update("INSERT INTO password_reset_tokens(token_hash,user_id,expires_at) VALUES (?,?,?)",PasswordResetService.digest(issued.token()),5L,Timestamp.from(clock.instant().plusSeconds(900)));
        assertFalse(issued.toString().contains(issued.token()));assertNotEquals(issued.token(),PasswordResetService.digest(issued.token()));
    }
    @Test void nonexistentAccountCreatesNoToken(){assertTrue(service.issue("unknown@example.com").isEmpty());verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void getValidationDoesNotConsumeToken(){when(jdbc.queryForObject(anyString(),eq(Integer.class),anyString(),any(Timestamp.class))).thenReturn(1);assertTrue(service.usable(token));verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void malformedTokenNeverQueriesDatabase(){assertFalse(service.usable("invalid"));verifyNoInteractions(jdbc);}
    @Test void expiredOrReplacedTokenCannotChangePassword(){
        when(jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash=?",PasswordResetService.digest(token))).thenReturn(List.of(Map.of("user_id",5L)));
        when(jdbc.queryForList("SELECT id,email FROM users WHERE id=? FOR UPDATE",5L)).thenReturn(List.of(Map.of("id",5L,"email","member@example.com")));
        var e=assertThrows(ResponseStatusException.class,()->service.reset(token,password,password));assertEquals(410,e.getStatusCode().value());verifyNoInteractions(encoder);verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void validResetStoresEncodedPasswordAndRemovesAllTokens(){
        when(jdbc.queryForList("SELECT user_id FROM password_reset_tokens WHERE token_hash=?",PasswordResetService.digest(token))).thenReturn(List.of(Map.of("user_id",5L)));
        when(jdbc.queryForList("SELECT id,email FROM users WHERE id=? FOR UPDATE",5L)).thenReturn(List.of(Map.of("id",5L,"email","member@example.com")));
        when(jdbc.queryForList(startsWith("SELECT token_hash"),eq(PasswordResetService.digest(token)),eq(5L),any(Timestamp.class))).thenReturn(List.of(Map.of("token_hash",PasswordResetService.digest(token))));
        when(encoder.encode(password)).thenReturn("encoded-password");when(jdbc.update("UPDATE users SET password_hash=? WHERE id=?","encoded-password",5L)).thenReturn(1);
        assertEquals("member@example.com",service.reset(token,password,password));verify(jdbc).update("DELETE FROM password_reset_tokens WHERE user_id=?",5L);
        verify(jdbc).update("INSERT INTO admin_activity(actor_id,action,details) VALUES (?,'PASSWORD_RESET','Password changed using a single-use email reset link')",5L);
    }
    @Test void reusedTokenIsRejected(){var e=assertThrows(ResponseStatusException.class,()->service.reset(token,password,password));assertEquals(410,e.getStatusCode().value());verifyNoInteractions(encoder);}
    @Test void passwordValidationRejectsMismatchWeakAndTooManyUtf8Bytes(){
        assertThrows(ResponseStatusException.class,()->service.reset(token,password,"different"));
        assertThrows(ResponseStatusException.class,()->service.reset(token,"short","short"));
        assertThrows(ResponseStatusException.class,()->service.reset(token,"😀".repeat(19),"😀".repeat(19)));verifyNoInteractions(jdbc,encoder);
    }
}
