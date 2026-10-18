package com.mdm.web;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SessionPasswordVersionsTest {
    @Test void passwordChangeInvalidatesPreviousCredentialSnapshot(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class);var versions=new SessionPasswordVersions(jdbc);var session=new MockHttpSession();
        var principal=new SessionPasswordVersions.VersionedUser("member@example.com","old-encoded-password","MEMBER");principal.eraseCredentials();assertNull(principal.getPassword());versions.record(session,principal.version());
        when(jdbc.queryForList("SELECT password_hash FROM users WHERE email=?","member@example.com")).thenReturn(List.of(Map.of("password_hash","old-encoded-password")),List.of(Map.of("password_hash","new-encoded-password")));
        assertTrue(versions.matches(session,"member@example.com"));assertFalse(versions.matches(session,"member@example.com"));
    }
    @Test void legacySessionWithoutVersionMustSignInAgain(){var versions=new SessionPasswordVersions(mock(JdbcTemplate.class));assertFalse(versions.matches(new MockHttpSession(),"member@example.com"));}
}
