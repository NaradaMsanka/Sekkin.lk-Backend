package com.cosmetic.ecommerce.security;

import com.cosmetic.ecommerce.config.DemoEnvironmentGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DemoEnvironmentGuardTest {
    private MockEnvironment demo() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", DemoEnvironmentGuard.DATABASE_URL)
                .withProperty("spring.datasource.driver-class-name", "org.h2.Driver")
                .withProperty("server.address", "127.0.0.1");
        environment.setActiveProfiles("demo", "local-admin-bootstrap");
        return environment;
    }

    @Test void permitsIsolatedDemo() {
        assertDoesNotThrow(() -> new DemoEnvironmentGuard().postProcessEnvironment(demo(), null));
    }

    @Test void rejectsProductionProfilesBeforeDatabaseStartup() {
        for (String profile : new String[] {"prod", "production"}) {
            MockEnvironment environment = demo();
            environment.setActiveProfiles("demo", profile);
            assertThrows(IllegalStateException.class, () -> new DemoEnvironmentGuard().postProcessEnvironment(environment, null));
        }
    }

    @Test void rejectsExternalOrPersistentDatabases() {
        for (String url : new String[] {"jdbc:postgresql://localhost/live", "jdbc:h2:file:./data", "jdbc:h2:mem:other"}) {
            MockEnvironment environment = demo().withProperty("spring.datasource.url", url);
            assertThrows(IllegalStateException.class, () -> new DemoEnvironmentGuard().postProcessEnvironment(environment, null));
        }
    }

    @Test void rejectsNetworkExposureAndDriverOverrides() {
        assertThrows(IllegalStateException.class, () -> new DemoEnvironmentGuard().postProcessEnvironment(
                demo().withProperty("server.address", "0.0.0.0"), null));
        assertThrows(IllegalStateException.class, () -> new DemoEnvironmentGuard().postProcessEnvironment(
                demo().withProperty("spring.datasource.driver-class-name", "org.postgresql.Driver"), null));
    }

    @Test void leavesNormalConfigurationAlone() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost/live");
        environment.setActiveProfiles("production");
        assertDoesNotThrow(() -> new DemoEnvironmentGuard().postProcessEnvironment(environment, null));
    }
}
