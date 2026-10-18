package com.mdm;

import com.mdm.web.WorkService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final WorkService service=new WorkService(jdbc);
    private final WorkService.Actor aditi=new WorkService.Actor(10,"Aditi",false);
    private final WorkService.Actor admin=new WorkService.Actor(1,"Admin",true);
    private void item(String state,long owner) {
        Map<String,Object> row=new HashMap<>();row.put("team_id",3L);row.put("assigned_to",owner==0?null:owner);row.put("status",state);row.put("priority","HIGH");
        when(jdbc.queryForList(anyString(),eq(8L))).thenReturn(List.of(row));
        when(jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE id=?",Integer.class,3L)).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships WHERE user_id=? AND team_id=?",Integer.class,10L,3L)).thenReturn(1);
    }
    @Test void workflowRequiresApprovalAndTerminalItemsStayClosed() {
        assertFalse(WorkService.allowedTransition("IN_PROGRESS","RESOLVED",true));
        assertFalse(WorkService.allowedTransition("PENDING_APPROVAL","RESOLVED",false));
        assertTrue(WorkService.allowedTransition("PENDING_APPROVAL","RESOLVED",true));
        assertTrue(WorkService.allowedTransition("PENDING_APPROVAL","IN_PROGRESS",true));
        assertTrue(WorkService.allowedTransition("BLOCKED","IN_PROGRESS",false));
        assertFalse(WorkService.allowedTransition("RESOLVED","IN_PROGRESS",true));
    }
    @Test void nonMemberCannotReadItem() {
        item("OPEN",0);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships WHERE user_id=? AND team_id=?",Integer.class,10L,3L)).thenReturn(0);
        var error=assertThrows(ResponseStatusException.class,()->service.item(aditi,8));assertEquals(403,error.getStatusCode().value());
    }
    @Test void nonOwnerCannotEditEvenWithinTeam() {
        item("IN_PROGRESS",11);
        var error=assertThrows(ResponseStatusException.class,()->service.update(aditi,8,0,"BLOCKED","HIGH","Waiting"));
        assertEquals(403,error.getStatusCode().value());verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void lostClaimReturnsConflictAndDoesNotLogSuccess() {
        item("OPEN",0);
        when(jdbc.update(startsWith("UPDATE work_items"),eq(10L),eq(8L),eq(0))).thenReturn(0);
        var error=assertThrows(ResponseStatusException.class,()->service.claim(aditi,8,0));assertEquals(409,error.getStatusCode().value());
        verify(jdbc,never()).update(startsWith("INSERT INTO activity_logs"),anyLong(),anyLong(),anyString(),anyString());
    }
    @Test void staleUpdateReturnsConflictAndDoesNotLogSuccess() {
        item("IN_PROGRESS",10);
        var error=assertThrows(ResponseStatusException.class,()->service.update(aditi,8,0,"BLOCKED","HIGH","Waiting"));
        assertEquals(409,error.getStatusCode().value());
        verify(jdbc,never()).update(startsWith("INSERT INTO activity_logs"),anyLong(),anyLong(),anyString(),anyString());
    }
    @Test void ownerCannotApproveTheirOwnWorkWithoutAdminRole() {
        item("PENDING_APPROVAL",10);
        var error=assertThrows(ResponseStatusException.class,()->service.update(aditi,8,0,"RESOLVED","HIGH","Done"));
        assertEquals(400,error.getStatusCode().value());
    }
    @Test void validClaimLogsActor() {
        item("OPEN",0);
        when(jdbc.update(startsWith("UPDATE work_items"),eq(10L),eq(8L),eq(0))).thenReturn(1);
        service.claim(aditi,8,0);
        verify(jdbc).update(startsWith("INSERT INTO activity_logs"),eq(8L),eq(10L),eq("CLAIMED"),contains("Aditi"));
    }
    @Test void completedCreateTokenReplaysOriginalResultButRejectsChangedPayload() {
        String token=UUID.randomUUID().toString();
        Map<String,Object> request=new HashMap<>();request.put("kind","CREATE");request.put("completed",true);request.put("work_item_id",8L);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM teams WHERE id=?",Integer.class,3L)).thenReturn(1);
        when(jdbc.queryForObject("SELECT COUNT(*) FROM team_memberships WHERE user_id=? AND team_id=?",Integer.class,10L,3L)).thenReturn(1);
        when(jdbc.update(startsWith("INSERT INTO operation_requests"),eq(10L),eq(token),eq("CREATE"),anyString())).thenAnswer(inv->{request.putIfAbsent("fingerprint",inv.getArgument(4));return 1;});
        when(jdbc.queryForMap(startsWith("SELECT kind"),eq(10L),eq(token))).thenReturn(request);
        assertEquals(8,service.create(aditi,"Payment","Context","HIGH",3,"Check",token));
        assertEquals(8,service.create(aditi,"Payment","Context","HIGH",3,"Check",token));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.create(aditi,"Changed title","Context","HIGH",3,"Check",token)).getStatusCode().value());
        verify(jdbc,never()).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class),any(org.springframework.jdbc.support.KeyHolder.class));
    }
    @Test void duplicateCommentDoesNotAppendAnotherEvent() {
        item("IN_PROGRESS",10);String token=UUID.randomUUID().toString();
        Map<String,Object> request=new HashMap<>();request.put("kind","COMMENT");request.put("completed",true);request.put("work_item_id",8L);
        when(jdbc.update(startsWith("INSERT INTO operation_requests"),eq(10L),eq(token),eq("COMMENT"),anyString())).thenAnswer(inv->{request.putIfAbsent("fingerprint",inv.getArgument(4));return 1;});
        when(jdbc.queryForMap(startsWith("SELECT kind"),eq(10L),eq(token))).thenReturn(request);
        service.comment(aditi,8,"Update",token);service.comment(aditi,8,"Update",token);
        verify(jdbc,never()).update(startsWith("INSERT INTO activity_logs"),anyLong(),anyLong(),anyString(),anyString());
    }
    @Test void nonOwnerCannotTransferResponsibility() {
        item("IN_PROGRESS",11);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.reassign(aditi,8,0,12,"Coverage")).getStatusCode().value());
    }
    @Test void transferRejectsClosedWork() {
        item("RESOLVED",10);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.reassign(aditi,8,0,12,"Coverage")).getStatusCode().value());
    }
    @Test void transferNeedsCurrentVersionAndEligibleMember() {
        item("IN_PROGRESS",10);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.reassign(aditi,8,0,12,"Coverage")).getStatusCode().value());
        verify(jdbc,never()).update(startsWith("INSERT INTO activity_logs"),anyLong(),anyLong(),anyString(),anyString());
    }
    @Test void validTransferLogsReasonAndActor() {
        item("IN_PROGRESS",10);
        when(jdbc.update(startsWith("UPDATE work_items SET assigned_to"),eq(12L),eq(8L),eq(0),eq(12L))).thenReturn(1);
        service.reassign(aditi,8,0,12,"Shift handoff");
        verify(jdbc).update(startsWith("INSERT INTO activity_logs"),eq(8L),eq(10L),eq("REASSIGNED"),contains("Shift handoff"));
    }
}
