package com.mdm.web;
import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration
public class PasswordResetConfig {
    @Bean Clock passwordResetClock(){return Clock.systemUTC();}
    @Bean(name="passwordResetExecutor") ThreadPoolTaskExecutor passwordResetExecutor(){
        var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(2);executor.setMaxPoolSize(2);executor.setQueueCapacity(50);executor.setThreadNamePrefix("password-mail-");executor.setWaitForTasksToCompleteOnShutdown(false);return executor;
    }
}
