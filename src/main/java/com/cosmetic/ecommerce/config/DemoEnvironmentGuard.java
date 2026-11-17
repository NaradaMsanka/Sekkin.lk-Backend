package com.cosmetic.ecommerce.config;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Reject unsafe demo configuration before Hibernate can create any tables. */
public class DemoEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    public static final String DATABASE_URL = "jdbc:h2:mem:sekkin_demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (!profiles.contains("demo")) return;
        if (profiles.contains("production") || profiles.contains("prod")
                || !DATABASE_URL.equals(environment.getProperty("spring.datasource.url"))
                || !"org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"))
                || !"127.0.0.1".equals(environment.getProperty("server.address"))) {
            throw new IllegalStateException("The demo requires its isolated in-memory H2 database, loopback address and no production profiles.");
        }
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
