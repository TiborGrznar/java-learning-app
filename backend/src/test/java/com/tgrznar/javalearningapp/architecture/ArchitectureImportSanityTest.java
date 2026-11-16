package com.tgrznar.javalearningapp.architecture;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.MAIN_CLASSES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the architecture rules against passing vacuously: a rule that checks nothing (wrong package, tests
 * imported, classes not found) is green and worthless. The lower bounds are deliberately loose, they only
 * prove that the import sees the application.
 */
class ArchitectureImportSanityTest {

    @Test
    void importSeesTheProductionClasses() {
        assertThat(MAIN_CLASSES.size()).isGreaterThan(200);
    }

    @Test
    void importLeavesTestClassesOut() {
        assertThat(MAIN_CLASSES.stream().map(javaClass -> javaClass.getSimpleName()))
                .noneMatch(name -> name.endsWith("Test") || name.endsWith("TestSupport"));
    }

    @Test
    void importFindsControllersServicesAndEntities() {
        assertThat(MAIN_CLASSES.stream().filter(javaClass -> javaClass.isAnnotatedWith(RestController.class)).count())
                .isGreaterThanOrEqualTo(10);
        assertThat(MAIN_CLASSES.stream().filter(javaClass -> javaClass.isAnnotatedWith(Service.class)).count())
                .isGreaterThanOrEqualTo(10);
        assertThat(MAIN_CLASSES.stream().filter(javaClass -> javaClass.isAnnotatedWith(Entity.class)).count())
                .isGreaterThanOrEqualTo(8);
    }
}
