package com.tgrznar.javalearningapp.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Stream;

import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.MAIN_CLASSES;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/** Rules for the REST controllers: no entities in the API, and no endpoint without a role check. */
class ControllerArchitectureTest {

    /**
     * Controllers without {@code @PreAuthorize}, each for a reason. AuthController serves the public endpoints that
     * SecurityConfig lists one by one. UserController serves the signed-in user's own profile and password, open
     * to every role, and SecurityConfig requires authentication for it ({@code anyRequest().authenticated()}).
     * A new entry needs a written reason here.
     */
    private static final Set<String> WITHOUT_ROLE_CHECK = Set.of(
            "com.tgrznar.javalearningapp.auth.AuthController",
            "com.tgrznar.javalearningapp.user.UserController");

    private static final DescribedPredicate<JavaClass> DOCUMENTED_WITHOUT_ROLE_CHECK = DescribedPredicate.describe(
            "are one of the documented controllers without a role check",
            javaClass -> WITHOUT_ROLE_CHECK.contains(javaClass.getFullName()));

    private static final ArchCondition<JavaMethod> NOT_EXPOSE_ENTITIES =
            new ArchCondition<>("not take or return a JPA entity, also inside a generic type") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    Stream.concat(
                                    method.getReturnType().getAllInvolvedRawTypes().stream(),
                                    method.getParameterTypes().stream()
                                            .flatMap(type -> type.getAllInvolvedRawTypes().stream()))
                            .filter(type -> type.isAnnotatedWith(Entity.class))
                            .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " uses the entity " + type.getName())));
                }
            };

    private static final ArchCondition<JavaClass> HAVE_ROLE_CHECK =
            new ArchCondition<>("have @PreAuthorize on the class or on every endpoint method") {
                @Override
                public void check(JavaClass controller, ConditionEvents events) {
                    if (controller.isAnnotatedWith(PreAuthorize.class)) {
                        return;
                    }
                    controller.getMethods().stream()
                            .filter(method -> method.isAnnotatedWith(RequestMapping.class)
                                    || method.isMetaAnnotatedWith(RequestMapping.class))
                            .filter(method -> !method.isAnnotatedWith(PreAuthorize.class))
                            .forEach(method -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " is an endpoint without @PreAuthorize")));
                }
            };

    @Test
    void controllerMethodsDoNotExposeEntities() {
        methods()
                .that().arePublic()
                .and().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(NOT_EXPOSE_ENTITIES)
                .because("an entity in the API can leak fields such as the password hash")
                .check(MAIN_CLASSES);
    }

    @Test
    void everyEndpointHasARoleCheckExceptTheDocumentedControllers() {
        classes()
                .that().areAnnotatedWith(RestController.class)
                .and(not(DOCUMENTED_WITHOUT_ROLE_CHECK))
                .should(HAVE_ROLE_CHECK)
                .because("a forgotten @PreAuthorize lets every signed-in user reach the endpoint")
                .check(MAIN_CLASSES);
    }
}
