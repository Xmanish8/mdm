package com.mdm.web;
import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import jakarta.annotation.PostConstruct;

@Component @Profile("prod")
public class ProductionGuard {
    private final Environment env;
    public ProductionGuard(Environment env){this.env=env;}
    @PostConstruct void validate(){
        String base=env.getProperty("mdm.site.base-url","");URI uri=URI.create(base);
        require("https".equals(uri.getScheme())&&uri.getHost()!=null&&!uri.getHost().endsWith(".invalid")&&!uri.getHost().equals("localhost")&&uri.getRawUserInfo()==null&&uri.getRawQuery()==null&&uri.getRawFragment()==null&&(uri.getPath().isEmpty()||uri.getPath().equals("/")),"Set a real HTTPS SITE_BASE_URL before production startup");
        require(!"root".equalsIgnoreCase(env.getProperty("spring.datasource.username","root")),"Production requires a restricted database user");
        require(env.getProperty("spring.datasource.password","").length()>=24,"Production database password must be provided through secrets storage");
        require(env.getProperty("mdm.security.rate-secret","").length()>=32,"Production requires a strong shared rate-limit secret");
        require(!env.getProperty("mdm.demo.enabled",Boolean.class,false),"Demo accounts cannot be seeded in production");
        require(env.getProperty("mdm.legal.operator","").length()>=2&&env.getProperty("mdm.legal.contact","").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Configure the legal operator and contact email");
        require("never".equals(env.getProperty("spring.sql.init.mode")),"Production schema changes require a separate migration step");
        require(env.getProperty("mdm.security.production",Boolean.class,false)&&env.getProperty("server.servlet.session.cookie.secure",Boolean.class,false),"Production requires HTTPS enforcement and secure cookies");
        require(!env.getProperty("debug",Boolean.class,false)&&!env.getProperty("trace",Boolean.class,false),"Production debug and trace modes must remain disabled");
        if(env.getProperty("mdm.password-reset.email-enabled",Boolean.class,false)){
            require(!env.getProperty("spring.mail.host","").isBlank()&&env.getProperty("mdm.password-reset.from","").matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Password recovery requires an SMTP host and valid sender address");
            require(env.getProperty("spring.mail.properties.mail.smtp.starttls.required",Boolean.class,false)||env.getProperty("spring.mail.properties.mail.smtp.ssl.enable",Boolean.class,false),"Production password recovery requires encrypted SMTP");
            if(env.getProperty("spring.mail.properties.mail.smtp.auth",Boolean.class,true))require(!env.getProperty("spring.mail.username","").isBlank()&&!env.getProperty("spring.mail.password","").isBlank(),"Authenticated SMTP requires a username and protected password");
        }
    }
    private void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
}
