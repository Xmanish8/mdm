package com.mdm;
import com.mdm.web.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest(properties={"spring.sql.init.mode=never","spring.datasource.password=unused","mdm.demo.enabled=false","mdm.password-reset.email-enabled=true"})
@AutoConfigureMockMvc
class ForgotPasswordWebTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean RateLimits limits;
    @MockitoBean SessionPasswordVersions versions;
    @MockitoBean PasswordResetService resets;
    @MockitoBean PasswordResetMail mail;
    String token="A".repeat(43);
    @BeforeEach void allow(){when(limits.allow(anyString(),anyString(),anyInt(),anyInt())).thenReturn(true);}
    MockHttpSession issued(){var session=new MockHttpSession();session.setAttribute("mdm.forgot.issued",System.currentTimeMillis()-2000);return session;}
    @Test void loginLinksToPublicRecoveryForm()throws Exception{
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/forgot-password\"")));
        mvc.perform(get("/forgot-password")).andExpect(status().isOk()).andExpect(content().string(containsString("Send reset link"))).andExpect(content().string(containsString("_csrf")));
    }
    @Test void knownAndUnknownEmailReceiveSameAcknowledgement()throws Exception{
        for(String email:new String[]{"member@example.com","unknown@example.com"})mvc.perform(post("/forgot-password").session(issued()).with(csrf()).param("email",email)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/forgot-password?sent"));
        verify(mail).request("member@example.com");verify(mail).request("unknown@example.com");verifyNoInteractions(resets,jdbc);
    }
    @Test void forgedPostAndHoneypotDoNotSendMail()throws Exception{
        mvc.perform(post("/forgot-password").param("email","member@example.com")).andExpect(status().isForbidden());
        mvc.perform(post("/forgot-password").session(issued()).with(csrf()).param("email","member@example.com").param("website","spam")).andExpect(status().is3xxRedirection());verifyNoInteractions(mail);
    }
    @Test void rateLimitStopsRequestsBeforeQueueing()throws Exception{
        when(limits.allow(eq("forgot-ip"),anyString(),eq(5),eq(900))).thenReturn(false);
        mvc.perform(post("/forgot-password").session(issued()).with(csrf()).param("email","member@example.com")).andExpect(status().isTooManyRequests());verifyNoInteractions(mail);
    }
    @Test void linkTokenMovesIntoServerSessionWithoutBeingConsumed()throws Exception{
        when(resets.usable(token)).thenReturn(true);var session=new MockHttpSession();
        mvc.perform(get("/reset-password").session(session).param("token",token)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reset-password"));assertEquals(token,session.getAttribute("mdm.password-reset.token"));
        mvc.perform(get("/reset-password").session(session)).andExpect(status().isOk()).andExpect(header().string("Referrer-Policy","strict-origin")).andExpect(content().string(containsString("Update password"))).andExpect(content().string(not(containsString(token))));
        verify(resets,never()).reset(any(),anyString(),anyString());
    }
    @Test void expiredLinkShowsSafeRecoveryOption()throws Exception{mvc.perform(get("/reset-password").param("token",token)).andExpect(status().isOk()).andExpect(content().string(containsString("Request a new link"))).andExpect(content().string(not(containsString("name=\"password\""))));}
    @Test void successfulResetEndsSessionAndRequiresLogin()throws Exception{
        var session=new MockHttpSession();session.setAttribute("mdm.password-reset.token",token);when(resets.reset(token,"test-only-password","test-only-password")).thenReturn("member@example.com");
        mvc.perform(post("/reset-password").session(session).with(csrf()).param("password","test-only-password").param("confirmation","test-only-password")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?reset"));
        assertTrue(session.isInvalid());verify(mail).changed("member@example.com");
    }
    @Test void validationDoesNotReflectSubmittedPassword()throws Exception{
        var session=new MockHttpSession();session.setAttribute("mdm.password-reset.token",token);when(resets.reset(token,"never-reflect-this","different")).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,"The two passwords do not match."));
        mvc.perform(post("/reset-password").session(session).with(csrf()).param("password","never-reflect-this").param("confirmation","different")).andExpect(status().isBadRequest()).andExpect(content().string(containsString("passwords do not match"))).andExpect(content().string(not(containsString("never-reflect-this"))));verifyNoInteractions(mail);
    }
    @Test void oldSignedInSessionIsRejectedOnPrivateRoutes()throws Exception{
        when(versions.matches(any(),anyString())).thenReturn(false);
        mvc.perform(get("/").with(user("member@example.com"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?expired"));
    }
}
