package com.mdm;

import com.mdm.web.WorkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"spring.sql.init.mode=never","spring.datasource.password=unused","mdm.demo.enabled=false"})
@AutoConfigureMockMvc
class WebFlowTest {
    @Autowired MockMvc mvc;
    @MockitoBean WorkService service;
    @MockitoBean org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean com.mdm.web.RateLimits limits;
    @MockitoBean com.mdm.web.SessionPasswordVersions passwordVersions;
    @BeforeEach void data() {
        when(limits.allow(anyString(),anyString(),anyInt(),anyInt())).thenReturn(true);
        when(passwordVersions.matches(any(),anyString())).thenReturn(true);
        var actor=new WorkService.Actor(10,"Aditi",false);
        when(service.actor(anyString())).thenReturn(actor);
        when(service.teams(any())).thenReturn(List.of(Map.of("id",3L,"name","Operations")));
        when(service.summary(any())).thenReturn(Map.of("OPEN",23L,"BLOCKED",4L,"PENDING_APPROVAL",2L));
        when(service.nextStates(any(),any())).thenReturn(List.of("IN_PROGRESS","BLOCKED","PENDING_APPROVAL","CANCELLED"));
        when(service.candidates(any(),anyLong())).thenReturn(List.of(Map.of("id",12L,"name","Rahul")));
        Map<String,Object> row=new HashMap<>();row.put("id",8L);row.put("team_id",3L);row.put("team_name","Operations");
        row.put("title","Investigate payment");row.put("description","Customer transfer missing");row.put("status","IN_PROGRESS");
        row.put("priority","URGENT");row.put("assigned_to",10L);row.put("owner_name","Aditi");row.put("next_action","Check reference");row.put("version",0);
        when(service.item(any(),anyLong())).thenReturn(row);
        when(service.list(any(),anyString(),anyString(),anyString(),anyLong(),anyInt())).thenReturn(List.of(row));
        when(service.history(any(),anyLong(),anyLong())).thenReturn(List.of(Map.of("id",1L,"actor_name","Aditi","action","CREATED","details","Payment received <script>alert(1)</script>","created_at","2026-10-18 10:00")));
    }
    @Test void anonymousUserIsSentToLogin() throws Exception { mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login")); }
    @Test void loginPageRendersCsrfProtectedForm() throws Exception { mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("_csrf"))); }
    @Test void queueRendersWithFiltersAndData() throws Exception { mvc.perform(get("/").with(user("aditi@mdm.local"))).andExpect(status().isOk()).andExpect(content().string(containsString("Investigate payment"))); }
    @Test void detailRendersAndEscapesComments() throws Exception {
        mvc.perform(get("/work/8").with(user("aditi@mdm.local"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(containsString("Save changes")));
    }
    @Test void mutationWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/work/8/claim").with(user("aditi@mdm.local")).param("version","0")).andExpect(status().isForbidden());
        verify(service,never()).claim(any(),anyLong(),anyInt());
    }
    @Test void claimUsesSessionIdentityAndRedirects() throws Exception {
        mvc.perform(post("/work/8/claim").with(user("aditi@mdm.local")).with(csrf()).param("version","0"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/work/8"));
        verify(service).claim(new WorkService.Actor(10,"Aditi",false),8,0);
    }
    @Test void conflictIsRenderedWith409() throws Exception {
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Your copy is stale"))
            .when(service).claim(any(),anyLong(),anyInt());
        mvc.perform(post("/work/8/claim").with(user("aditi@mdm.local")).with(csrf()).param("version","0"))
            .andExpect(status().isConflict()).andExpect(content().string(containsString("Your copy is stale")));
    }
    @Test void dashboardIncludesBootstrapAndAuthorizedSummary() throws Exception {
        mvc.perform(get("/").with(user("aditi@mdm.local"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap-5.3.8.min.css"))).andExpect(content().string(containsString("Work overview")))
            .andExpect(content().string(containsString("<strong>23</strong>")));
    }
    @Test void detailContainsTransferFormAndDuplicateProtectionToken() throws Exception {
        mvc.perform(get("/work/8").with(user("aditi@mdm.local"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Transfer responsibility"))).andExpect(content().string(containsString("name=\"token\"")));
    }
    @Test void memberCannotReadAdministrationOrCreateTeams() throws Exception {
        mvc.perform(get("/admin").with(user("aditi@mdm.local"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/teams").with(user("aditi@mdm.local")).with(csrf()).param("name","Unauthorized team")).andExpect(status().isForbidden());
        verifyNoInteractions(jdbc);
    }
    @Test void adminPageRendersManagementForms() throws Exception {
        when(service.actor(anyString())).thenReturn(new WorkService.Actor(1,"Admin",true));
        mvc.perform(get("/admin").with(user("manish@mdm.local").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Create a member"))).andExpect(content().string(containsString("Grant team access")));
    }
    @Test void transferUsesAuthenticatedActorAndVersion() throws Exception {
        mvc.perform(post("/work/8/reassign").with(user("aditi@mdm.local")).with(csrf()).param("version","0").param("owner","12").param("reason","Shift handoff"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/work/8"));
        verify(service).reassign(new WorkService.Actor(10,"Aditi",false),8,0,12,"Shift handoff");
    }
    @Test void adminAnalyticsRendersWithoutPersonalData()throws Exception{
        when(service.actor(anyString())).thenReturn(new WorkService.Actor(1,"Admin",true));
        mvc.perform(get("/admin/analytics").with(user("manish@mdm.local").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Public-page overview"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("password_hash"))));
    }
}
