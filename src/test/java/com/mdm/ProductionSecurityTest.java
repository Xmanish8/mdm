package com.mdm;
import com.mdm.web.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"spring.sql.init.mode=never","spring.datasource.password=unused","mdm.demo.enabled=false","mdm.analytics.enabled=true"})
@AutoConfigureMockMvc
class ProductionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean RateLimits limits;
    @BeforeEach void allow(){when(limits.allow(anyString(),anyString(),anyInt(),anyInt())).thenReturn(true);}
    @Test void publicPageHasLocalAssetsCspAndConsent()throws Exception{
        mvc.perform(get("/welcome")).andExpect(status().isOk()).andExpect(header().string("Content-Security-Policy",containsString("default-src 'self'")))
            .andExpect(header().string("X-Frame-Options","DENY")).andExpect(header().string("Referrer-Policy","no-referrer"))
            .andExpect(content().string(containsString("cookie-consent"))).andExpect(content().string(containsString("/assets/social-preview.png")))
            .andExpect(content().string(not(containsString("cdn.jsdelivr.net"))));
    }
    @Test void legalPagesArePublic()throws Exception{for(String path:new String[]{"/privacy","/terms"})mvc.perform(get(path)).andExpect(status().isOk());}
    @Test void crawlerFilesExposeOnlyPublicPages()throws Exception{
        mvc.perform(get("/robots.txt")).andExpect(status().isOk()).andExpect(content().string(containsString("Disallow: /")));
        mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andExpect(content().string(containsString("/welcome"))).andExpect(content().string(not(containsString("/work/"))));
    }
    @Test void signupUploadsAndCrossOriginAreRejected()throws Exception{
        mvc.perform(get("/signup")).andExpect(status().isNotFound());
        mvc.perform(post("/upload").with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/work").contentType("multipart/form-data; boundary=test").with(csrf())).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/welcome").header("Origin","https://attacker.invalid")).andExpect(status().isForbidden());
    }
    @Test void loginRateLimitReturns429BeforeAuthentication()throws Exception{
        when(limits.allow(eq("login-ip"),anyString(),eq(30),eq(900))).thenReturn(false);
        mvc.perform(post("/login").with(csrf()).param("username","a@example.com").param("password","anything")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        verifyNoInteractions(jdbc);
    }
    @Test void honeypotAndFreshBotSubmissionsAreRejected()throws Exception{
        MockHttpSession session=new MockHttpSession();session.setAttribute("mdm.login.issued",System.currentTimeMillis()-2000);
        mvc.perform(post("/login").session(session).with(csrf()).param("username","a@example.com").param("password","anything").param("website","spam")).andExpect(status().isBadRequest());
        mvc.perform(post("/login").with(csrf()).param("username","a@example.com").param("password","anything")).andExpect(status().isBadRequest());verifyNoInteractions(jdbc);
    }
    @Test void analyticsNeedsConsentAndWhitelistedPublicPath()throws Exception{
        mvc.perform(post("/analytics/pageview").with(csrf()).contentType("application/json").content("{\"page\":\"/welcome\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/analytics/pageview").with(csrf()).header("X-MDM-Consent","analytics-v1").contentType("application/json").content("{\"page\":\"/work/8?secret=test\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(jdbc);
        mvc.perform(post("/analytics/pageview").with(csrf()).header("X-MDM-Consent","analytics-v1").contentType("application/json").content("{\"page\":\"/welcome\"}")).andExpect(status().isNoContent());
        verify(jdbc).update(org.mockito.ArgumentMatchers.startsWith("INSERT INTO analytics_daily"),eq("/welcome"));
    }
    @Test void staticAssetsAndCustom404TemplateExist()throws Exception{
        mvc.perform(get("/assets/favicon.svg")).andExpect(status().isOk());
        mvc.perform(get("/assets/social-preview.png")).andExpect(status().isOk());
        mvc.perform(get("/not-a-real-page")).andExpect(status().isNotFound());
    }
    @Test void authenticatedPagesAreNotIndexable()throws Exception{mvc.perform(get("/login")).andExpect(header().string("X-Robots-Tag","noindex, nofollow"));}
    @Test void databaseFailureDuringThrottlingFailsClosed()throws Exception{
        when(limits.allow(anyString(),anyString(),anyInt(),anyInt())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal detail"));
        mvc.perform(post("/login").with(csrf()).param("username","a@example.com").param("password","test-only")).andExpect(status().isServiceUnavailable());verifyNoInteractions(jdbc);
    }
    @Test void errorPageDoesNotExposeExceptionDetails()throws Exception{
        mvc.perform(get("/error").requestAttr("jakarta.servlet.error.status_code",500).requestAttr("jakarta.servlet.error.exception",new IllegalStateException("never-expose-this-detail")))
            .andExpect(status().isInternalServerError()).andExpect(content().string(not(containsString("never-expose-this-detail"))));
    }
}
