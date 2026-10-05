package com.meditrack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Boots the whole app, so it needs a running MySQL. Opt in with RUN_DB_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@SpringBootTest
class MeditrackBackendApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context (entities, repositories, security config) wires up correctly.
    }
}
