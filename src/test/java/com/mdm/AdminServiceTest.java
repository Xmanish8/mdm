package com.mdm;
import com.mdm.web.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminServiceTest {
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    PasswordEncoder encoder=mock(PasswordEncoder.class);
    AdminService service=new AdminService(jdbc,encoder);
    WorkService.Actor member=new WorkService.Actor(10,"Member",false),admin=new WorkService.Actor(1,"Admin",true);
    @Test void memberCannotManageTeamsUsersOrMemberships(){
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.createTeam(member,"Ops")).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.createUser(member,"New","new@example.com","longpassword12"));
        assertThrows(ResponseStatusException.class,()->service.addMember(member,10,3));verifyNoInteractions(jdbc,encoder);
    }
    @Test void adminCannotCreateWeakPasswords(){assertThrows(ResponseStatusException.class,()->service.createUser(admin,"New","new@example.com","weak"));verifyNoInteractions(jdbc,encoder);}
    @Test void newUsersAreMembersWithEncodedPasswords(){
        when(encoder.encode("strongpassword12")).thenReturn("bcrypt-hash");service.createUser(admin,"New","NEW@example.com","strongpassword12");
        verify(jdbc).update("INSERT INTO users(name,email,password_hash) VALUES (?,?,?)","New","new@example.com","bcrypt-hash");
        verify(jdbc).update("INSERT INTO admin_activity(actor_id,action,details) VALUES (?,?,?)",1L,"USER_CREATED","new@example.com");
    }
    @Test void invalidMemberCannotBeAdded(){when(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=?",Integer.class,999L)).thenReturn(0);assertThrows(ResponseStatusException.class,()->service.addMember(admin,999,3));}
}
