package com.cosmetic.ecommerce.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetConfiguration {
    @Bean public Clock passwordResetClock() { return Clock.systemUTC(); }

    @Bean("passwordResetExecutor")
    public ThreadPoolTaskExecutor passwordResetExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("password-reset-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }

    @Bean
    public InitializingBean passwordResetMailValidation(PasswordResetProperties reset, MailProperties mail) {
        return () -> {
            if (reset.isEnabled() && (blank(mail.getHost()) || blank(mail.getUsername()) || blank(mail.getPassword())))
                throw new IllegalStateException("Password reset SMTP host, username and password must be configured in the backend environment.");
        };
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
