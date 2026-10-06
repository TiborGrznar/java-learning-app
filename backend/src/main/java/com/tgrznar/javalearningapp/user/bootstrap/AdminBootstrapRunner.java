package com.tgrznar.javalearningapp.user.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the first-administrator check once, right after the application context is ready.
 * By then Flyway has already migrated the schema. Kept as a thin wrapper so that all the
 * logic stays in AdminBootstrapService, where it can be tested directly.
 * An exception thrown here stops the application with a failed startup.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapService adminBootstrapService;

    public AdminBootstrapRunner(AdminBootstrapService adminBootstrapService) {
        this.adminBootstrapService = adminBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminBootstrapService.createFirstAdminIfMissing();
    }
}