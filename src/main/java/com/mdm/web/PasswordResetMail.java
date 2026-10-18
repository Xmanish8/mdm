package com.mdm.web;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.*;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.core.task.TaskExecutor;
import org.slf4j.*;

@Service
public class PasswordResetMail {
    private static final Logger log=LoggerFactory.getLogger(PasswordResetMail.class);
    private final PasswordResetService resets;private final JavaMailSender mail;private final TaskExecutor executor;
    private final boolean enabled;private final String from,baseUrl;
    public PasswordResetMail(PasswordResetService resets,JavaMailSender mail,@Qualifier("passwordResetExecutor") TaskExecutor executor,
        @Value("${mdm.password-reset.email-enabled:false}") boolean enabled,@Value("${mdm.password-reset.from:}") String from,
        @Value("${mdm.site.base-url:http://localhost:8080}") String baseUrl){this.resets=resets;this.mail=mail;this.executor=executor;this.enabled=enabled;this.from=from;this.baseUrl=baseUrl.replaceAll("/+$","");}
    public void request(String email){submit(()->{
        if(!enabled)return;
        var issued=resets.issue(email);if(issued.isEmpty())return;
        var reset=issued.get();
        try{send(reset.email(),"Reset your MDM password","A password reset was requested for your MDM account.\n\nOpen this link within 15 minutes:\n"+baseUrl+"/reset-password?token="+reset.token()+"\n\nOnly the newest link works, and each link can be used once. If you did not request this, ignore the message. Your password has not changed.");}
        catch(MailException error){resets.revoke(reset.token());log.warn("Password reset delivery failed; the undelivered token was revoked. Check SMTP configuration.");}
    });}
    public void changed(String email){submit(()->{if(enabled)send(email,"Your MDM password was changed","Your MDM password has been changed and existing signed-in sessions will no longer be accepted.\n\nIf this was not you, contact your workspace administrator immediately. No password is included in this message.");});}
    private void send(String to,String subject,String body){var message=new SimpleMailMessage();message.setFrom(from);message.setTo(to);message.setSubject(subject);message.setText(body);mail.send(message);}
    private void submit(Runnable work){try{executor.execute(()->{try{work.run();}catch(RuntimeException error){log.warn("Password recovery background work failed. No request details were logged.");}});}catch(org.springframework.core.task.TaskRejectedException error){log.warn("Password recovery queue is full. No request details were logged.");}}
}
