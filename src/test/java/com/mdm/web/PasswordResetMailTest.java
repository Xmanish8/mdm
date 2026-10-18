package com.mdm.web;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PasswordResetMailTest {
    PasswordResetService service=mock(PasswordResetService.class);JavaMailSender sender=mock(JavaMailSender.class);
    PasswordResetMail mail(boolean enabled){return new PasswordResetMail(service,sender,Runnable::run,enabled,"security@example.com","https://workspace.example.com");}
    @Test void disabledMailDoesNotIssueTokens(){mail(false).request("member@example.com");verifyNoInteractions(service,sender);}
    @Test void unknownAddressDoesNotReceiveEmail(){when(service.issue("unknown@example.com")).thenReturn(Optional.empty());mail(true).request("unknown@example.com");verifyNoInteractions(sender);}
    @Test void deliveredMessageUsesConfiguredTrustedBaseUrl(){
        when(service.issue("member@example.com")).thenReturn(Optional.of(new PasswordResetService.Issued("member@example.com","A".repeat(43))));mail(true).request("member@example.com");
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(sender).send(message.capture());assertEquals("security@example.com",message.getValue().getFrom());assertArrayEquals(new String[]{"member@example.com"},message.getValue().getTo());assertTrue(message.getValue().getText().contains("https://workspace.example.com/reset-password?token="));
    }
    @Test void failedDeliveryRevokesUndeliveredToken(){
        String token="A".repeat(43);when(service.issue("member@example.com")).thenReturn(Optional.of(new PasswordResetService.Issued("member@example.com",token)));doThrow(new MailSendException("test failure")).when(sender).send(any(SimpleMailMessage.class));mail(true).request("member@example.com");verify(service).revoke(token);
    }
}
