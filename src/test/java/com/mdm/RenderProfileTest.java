package com.mdm;

import com.mdm.web.RateLimits;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"prod","render"})
@SpringBootTest(properties={"RENDER_EXTERNAL_URL=https://mdm-render-check.onrender.com","PORT=10000","spring.datasource.password=test-only-render-db-password","mdm.security.rate-secret=test-only-render-rate-secret-1234567890","mdm.legal.operator=Test operator","mdm.legal.contact=ops@example.com"})
@AutoConfigureMockMvc
class RenderProfileTest {
    @Autowired Environment environment;
    @Autowired MockMvc mvc;
    @MockitoBean JdbcTemplate jdbc;
    @MockitoBean RateLimits limits;
    @Test void renderSettingsKeepProductionControlsAndUsePlatformValues() {
        assertEquals("10000",environment.getProperty("server.port"));
        assertEquals("0.0.0.0",environment.getProperty("server.address"));
        assertEquals("https://mdm-render-check.onrender.com",environment.getProperty("mdm.site.base-url"));
        assertEquals("optional:configtree:/etc/secrets/",environment.getProperty("spring.config.import"));
        assertEquals("never",environment.getProperty("spring.sql.init.mode"));
        assertTrue(environment.getProperty("mdm.security.production",Boolean.class));
        assertTrue(environment.getProperty("server.servlet.session.cookie.secure",Boolean.class));
        assertFalse(environment.getProperty("mdm.demo.enabled",Boolean.class));
        assertEquals("60000",environment.getProperty("spring.datasource.hikari.connection-timeout"));
        assertEquals("60000",environment.getProperty("spring.datasource.hikari.initialization-fail-timeout"));
        assertEquals("15000",environment.getProperty("spring.datasource.hikari.data-source-properties.connectTimeout"));
        assertEquals("60000",environment.getProperty("spring.datasource.hikari.data-source-properties.socketTimeout"));
    }
    @Test void forwardedHttpsDoesNotLoopBehindRenderProxy() throws Exception {
        mvc.perform(get("http://mdm-render-check.onrender.com/welcome")
                .header("X-Forwarded-Proto","https").header("X-Forwarded-Host","mdm-render-check.onrender.com"))
            .andExpect(status().isOk()).andExpect(header().exists("Strict-Transport-Security"));
    }
    @Test void foreignOriginIsStillRejected() throws Exception {
        mvc.perform(get("https://mdm-render-check.onrender.com/welcome").secure(true).header("Origin","https://attacker.invalid"))
            .andExpect(status().isForbidden());
    }
}
