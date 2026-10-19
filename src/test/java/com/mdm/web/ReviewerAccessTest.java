package com.mdm.web;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReviewerAccessTest {
    JdbcTemplate jdbc;
    BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(4);
    String publicPassword="test-only-reviewer-password";
    MockEnvironment environment(){return new MockEnvironment().withProperty("mdm.reviewer.enabled","true")
        .withProperty("mdm.reviewer.password",publicPassword).withProperty("spring.datasource.username","mdm_review_app");}
    @BeforeEach void setup(){
        jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT DATABASE()",String.class)).thenReturn("mdm_review");
        when(jdbc.queryForList("SELECT marker FROM reviewer_workspace WHERE id=1",String.class)).thenReturn(List.of("MDM_PUBLIC_REVIEW_ONLY"));
        String hash=passwords.encode(publicPassword);
        when(jdbc.queryForList(eq("SELECT role,password_hash FROM users WHERE email=?"),anyString())).thenAnswer(call->{
            String email=call.getArgument(1);
            String role=email.equals("manish.review@mdm.local")?"ADMIN":"MEMBER";
            return List.of(Map.of("role",role,"password_hash",hash));
        });
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM team_memberships"),eq(Integer.class),anyString(),anyString())).thenReturn(1);
    }
    @Test void disabledModeNeverReadsDatabaseOrPublishesCredentials(){
        ReviewerAccess access=new ReviewerAccess(jdbc,passwords,environment().withProperty("mdm.reviewer.enabled","false"));
        access.validate();assertNull(access.view());verifyNoInteractions(jdbc);
    }
    @Test void rejectsThePrivateDatabaseAccountBeforeReadingAnything(){
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment().withProperty("spring.datasource.username","mdm_app")).validate());
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsThePrivateWorkspaceBeforeReadingItsAccounts(){
        when(jdbc.queryForObject("SELECT DATABASE()",String.class)).thenReturn("mdm");
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment()).validate());
        verify(jdbc,never()).queryForList(eq("SELECT role,password_hash FROM users WHERE email=?"),anyString());
    }
    @Test void requiresTheSampleOnlyDatabaseMarker(){
        when(jdbc.queryForList("SELECT marker FROM reviewer_workspace WHERE id=1",String.class)).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment()).validate());
    }
    @Test void rejectsMissingPublicCredential(){
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment().withProperty("mdm.reviewer.password","")).validate());
        verifyNoInteractions(jdbc);
    }
    @Test void rejectsCredentialsThatDoNotMatchPreparedAccounts(){
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment().withProperty("mdm.reviewer.password","different-test-password")).validate());
    }
    @Test void rejectsUnexpectedRoles(){
        when(jdbc.queryForList("SELECT role,password_hash FROM users WHERE email=?","manish.review@mdm.local"))
            .thenReturn(List.of(Map.of("role","MEMBER","password_hash",passwords.encode(publicPassword))));
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment()).validate());
    }
    @Test void rejectsIncompleteMemberships(){
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM team_memberships"),eq(Integer.class),anyString(),anyString())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->new ReviewerAccess(jdbc,passwords,environment()).validate());
    }
    @Test void validSampleWorkspacePublishesOnlyTheFourReviewProfiles(){
        ReviewerAccess access=new ReviewerAccess(jdbc,passwords,environment());access.validate();
        assertEquals(4,access.view().profiles().size());assertEquals(publicPassword,access.view().password());
        assertEquals(1,access.view().profiles().stream().filter(profile->profile.role().equals("ADMIN")).count());
        assertTrue(access.view().profiles().stream().allMatch(profile->profile.email().endsWith(".review@mdm.local")));
    }
}
