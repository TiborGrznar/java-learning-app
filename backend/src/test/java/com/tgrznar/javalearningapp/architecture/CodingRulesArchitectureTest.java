package com.tgrznar.javalearningapp.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tgrznar.javalearningapp.architecture.ArchitectureTestSupport.MAIN_CLASSES;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** General coding rules of the project. */
class CodingRulesArchitectureTest {

    private static final ArchCondition<JavaClass> NOT_CALL_RUNTIME_EXEC =
            new ArchCondition<>("not call Runtime.exec") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                        if (call.getTargetOwner().getFullName().equals("java.lang.Runtime")
                                && call.getTarget().getName().equals("exec")) {
                            events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                        }
                    }
                }
            };

    @Test
    void dependenciesAreInjectedThroughConstructors() {
        noFields().should().beAnnotatedWith(Autowired.class)
                .because("constructor injection keeps dependencies final and visible")
                .check(MAIN_CLASSES);
        noMethods().should().beAnnotatedWith(Autowired.class)
                .because("setter injection hides required dependencies")
                .check(MAIN_CLASSES);
    }

    @Test
    void theApplicationNeverStartsOperatingSystemProcesses() {
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("java.lang.ProcessBuilder")
                .because("user code never runs in the application JVM, only the separate runner starts processes")
                .check(MAIN_CLASSES);
        noClasses().should(NOT_CALL_RUNTIME_EXEC)
                .because("user code never runs in the application JVM, only the separate runner starts processes")
                .check(MAIN_CLASSES);
    }

    @Test
    void noStandardStreamsInProductionCode() {
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(MAIN_CLASSES);
    }

    @Test
    void noJavaUtilLogging() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(MAIN_CLASSES);
    }
}
