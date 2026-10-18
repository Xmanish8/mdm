package com.mdm;
import com.mdm.web.RateLimits;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.sql.init.mode=never","spring.datasource.password=unused","mdm.demo.enabled=false","mdm.security.production=true","mdm.site.base-url=https://workspace.example.com"})
@AutoConfigureMockMvc
class HttpsEnforcementTest {
    @Autowired MockMvc mvc;
    @MockitoBean RateLimits limits;
    @Test void insecurePublicRequestsAreRedirectedToHttps()throws Exception{mvc.perform(get("http://workspace.example.com/welcome")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://workspace.example.com/welcome"));}
    @Test void secureResponsesHaveHsts()throws Exception{mvc.perform(get("https://workspace.example.com/welcome").secure(true)).andExpect(status().isOk()).andExpect(header().exists("Strict-Transport-Security"));}
}
