package com.mdm.web;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class ProductionGuardTest {
    MockEnvironment environment(){return new MockEnvironment().withProperty("mdm.site.base-url","https://workspace.example.com").withProperty("spring.datasource.username","mdm_app").withProperty("spring.datasource.password","test-only-password-1234567890").withProperty("mdm.security.rate-secret","test-only-rate-secret-1234567890123456").withProperty("mdm.legal.operator","Test organization").withProperty("mdm.legal.contact","ops@example.com").withProperty("spring.sql.init.mode","never").withProperty("mdm.security.production","true").withProperty("server.servlet.session.cookie.secure","true");}
    @Test void validProductionSettingsPass(){assertDoesNotThrow(()->new ProductionGuard(environment()).validate());}
    @Test void rejectsLocalHttpDomain(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("mdm.site.base-url","http://localhost:8080")).validate());}
    @Test void rejectsRootDatabaseAccount(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("spring.datasource.username","root")).validate());}
    @Test void rejectsDemoAndDebugModes(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("mdm.demo.enabled","true")).validate());assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("debug","true")).validate());}
    @Test void rejectsWeakOrMissingSecret(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("mdm.security.rate-secret","")).validate());}
    @Test void rejectsAutomaticSchemaWrites(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("spring.sql.init.mode","always")).validate());}
    @Test void enabledRecoveryRequiresConfiguredSmtp(){assertThrows(IllegalStateException.class,()->new ProductionGuard(environment().withProperty("mdm.password-reset.email-enabled","true")).validate());}
    @Test void enabledRecoveryRequiresEncryptedSmtp(){var settings=environment().withProperty("mdm.password-reset.email-enabled","true").withProperty("spring.mail.host","smtp.example.com").withProperty("mdm.password-reset.from","security@example.com").withProperty("spring.mail.username","smtp-user").withProperty("spring.mail.password","test-only-secret");assertThrows(IllegalStateException.class,()->new ProductionGuard(settings).validate());assertDoesNotThrow(()->new ProductionGuard(settings.withProperty("spring.mail.properties.mail.smtp.starttls.required","true")).validate());}
}
