package com.mdm;

import com.mdm.web.RateLimits;
import com.mdm.web.WorkService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses real form authentication, BCrypt, session storage and password-version checks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.address=127.0.0.1","spring.datasource.url=jdbc:h2:mem:login_flow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver","spring.sql.init.mode=never","mdm.demo.enabled=false","mdm.password-reset.email-enabled=false"})
@AutoConfigureMockMvc
class LoginFlowTest {
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoBean RateLimits limits;
    @MockitoBean WorkService work;
    @BeforeEach void setup(){
        jdbc.execute("CREATE TABLE IF NOT EXISTS users(email VARCHAR(150) PRIMARY KEY,password_hash VARCHAR(255),role VARCHAR(20))");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users VALUES (?,?,?)","alice@mdm.local",passwords.encode("test-only-login-password"),"MEMBER");
        when(limits.allow(anyString(),anyString(),anyInt(),anyInt())).thenReturn(true);
        when(work.actor(anyString())).thenReturn(new WorkService.Actor(1,"Alice",false));
        when(work.teams(any())).thenReturn(List.of());
        when(work.list(any(),anyString(),anyString(),anyString(),anyLong(),anyInt())).thenReturn(List.of());
        when(work.summary(any())).thenReturn(Map.of("OPEN",0L,"IN_PROGRESS",0L,"BLOCKED",0L,"PENDING_APPROVAL",0L,"RESOLVED",0L,"CANCELLED",0L));
    }
    private MockHttpSession form() throws Exception {
        var result=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession();
        session.setAttribute("mdm.login.issued",System.currentTimeMillis()-2000);
        return session;
    }
    @Test void correctPasswordRedirectsToWorkingDashboard() throws Exception {
        var result=mvc.perform(post("/login").session(form()).with(csrf()).param("username","alice@mdm.local").param("password","test-only-login-password"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/")).andReturn();
        mvc.perform(get("/").session((MockHttpSession)result.getRequest().getSession()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Work overview")));
    }
    @Test void incorrectPasswordReturnsToWorkingLoginPage() throws Exception {
        var result=mvc.perform(post("/login").session(form()).with(csrf()).param("username","alice@mdm.local").param("password","test-only-wrong-password"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/login?error")).andReturn();
        mvc.perform(get("/login").param("error","").session((MockHttpSession)result.getRequest().getSession()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Email or password is incorrect.")));
    }
    @Test void staleLoginFormGetsFreshFormWithoutAuthenticating() throws Exception {
        mvc.perform(post("/login").session(form()).with(csrf().useInvalidToken()).param("username","alice@mdm.local").param("password","test-only-login-password"))
            .andExpect(status().isFound()).andExpect(redirectedUrl("/login?formExpired"));
        mvc.perform(get("/login").param("formExpired",""))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Your sign-in form expired")));
        mvc.perform(get("/")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
    }
    @Test void loginFromDifferentSiteRemainsForbidden() throws Exception {
        mvc.perform(post("/login").session(form()).with(csrf()).header("Origin","https://attacker.invalid").param("username","alice@mdm.local").param("password","test-only-login-password"))
            .andExpect(status().isForbidden());
    }
    @Test void browserFormAndCookiesWorkOverRealHttp() throws Exception {
        var client=java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String base="http://127.0.0.1:"+port;
        var page=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/login")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200,page.statusCode());
        var form=java.util.regex.Pattern.compile("<form[^>]*action=\"([^\"]+)\"").matcher(page.body());
        org.junit.jupiter.api.Assertions.assertTrue(form.find());
        var csrf=java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());
        org.junit.jupiter.api.Assertions.assertTrue(csrf.find());
        Thread.sleep(800); // The public form rejects submissions younger than 750 ms.
        String body="username=alice%40mdm.local&password=test-only-login-password&_csrf="+java.net.URLEncoder.encode(csrf.group(1),java.nio.charset.StandardCharsets.UTF_8);
        var login=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base).resolve(form.group(1))).header("Content-Type","application/x-www-form-urlencoded").POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(302,login.statusCode(),login.body());
        var dashboard=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base).resolve(login.headers().firstValue("Location").orElseThrow())).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        org.junit.jupiter.api.Assertions.assertEquals(200,dashboard.statusCode(),dashboard.body());
        org.junit.jupiter.api.Assertions.assertTrue(dashboard.body().contains("Work overview"));
    }
}
