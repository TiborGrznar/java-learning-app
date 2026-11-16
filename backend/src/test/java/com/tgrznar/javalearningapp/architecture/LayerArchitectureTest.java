package com.tgrznar.javalearningapp.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.MAIN_CLASSES;
import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.outermostClass;
import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.outermostClassIsAnnotatedWith;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** Layer rules: controller -> service -> repository, DTOs are records, transactions live in services. */
class LayerArchitectureTest {

    /**
     * Classes outside the service layer that may read a repository, each for a reason. The security filter and the
     * configuration that builds it read the role and the active flag on every request (section 17 of the design
     * notes). The enrollment gate asks whether a student has a class. The join code generator checks that a
     * generated code is unused. A new entry needs a written reason here.
     */
    private static final Set<String> MAY_USE_REPOSITORIES = Set.of(
            "com.tgrznar.javalearningapp.auth.security.JwtAuthenticationFilter",
            "com.tgrznar.javalearningapp.auth.security.SecurityConfig",
            "com.tgrznar.javalearningapp.schoolclass.enrollment.EnrollmentGateInterceptor",
            "com.tgrznar.javalearningapp.schoolclass.JoinCodeGenerator");

    private static final DescribedPredicate<JavaClass> SERVICE_OR_DOCUMENTED_EXCEPTION = DescribedPredicate.describe(
            "are services or one of the documented exceptions",
            javaClass -> outermostClass(javaClass).isAnnotatedWith(Service.class)
                    || MAY_USE_REPOSITORIES.contains(outermostClass(javaClass).getFullName()));

    @Test
    void controllersAreNotUsedByAnyOtherClass() {
        noClasses()
                .that(not(outermostClassIsAnnotatedWith(RestController.class)))
                .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
                .because("a controller is the entry of the application, other code must not call it")
                .check(MAIN_CLASSES);
    }

    @Test
    void repositoriesAreUsedOnlyByServicesAndDocumentedExceptions() {
        noClasses()
                .that(not(SERVICE_OR_DOCUMENTED_EXCEPTION))
                .and().areNotAssignableTo(Repository.class)
                .should().dependOnClassesThat().areAssignableTo(Repository.class)
                .because("controllers reach the database only through a service, see MAY_USE_REPOSITORIES")
                .check(MAIN_CLASSES);
    }

    @Test
    void classesInDtoPackagesAreRecordsOrEnums() {
        classes()
                .that().resideInAPackage("..dto..")
                .and().doNotHaveSimpleName("package-info")
                .should(ArchCondition.from(
                        DescribedPredicate.describe("be a record or an enum",
                                (JavaClass javaClass) -> javaClass.isRecord() || javaClass.isEnum())))
                .check(MAIN_CLASSES);
    }

    @Test
    void transactionsAreDeclaredOnlyInServices() {
        noClasses()
                .that(not(outermostClassIsAnnotatedWith(Service.class)))
                .should().beAnnotatedWith(Transactional.class)
                .check(MAIN_CLASSES);

        noMethods()
                .that().areDeclaredInClassesThat(not(outermostClassIsAnnotatedWith(Service.class)))
                .should().beAnnotatedWith(Transactional.class)
                .check(MAIN_CLASSES);
    }
}
