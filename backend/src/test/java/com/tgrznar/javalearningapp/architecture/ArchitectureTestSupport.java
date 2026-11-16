package com.tgrznar.javalearningapp.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import java.lang.annotation.Annotation;

/**
 * Imports the production classes once; every architecture test class shares the result. Test classes are left
 * out on purpose, the rules describe the application, not its tests.
 */
final class ArchitectureTestSupport {

    static final String BASE_PACKAGE = "com.tgrznar.javalearningapp";

    static final JavaClasses MAIN_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    private ArchitectureTestSupport() {
    }

    /**
     * The outermost class a nested class (inner, anonymous or local) belongs to; a top-level class is its own
     * outermost class. Stereotype annotations such as {@code @Service} are not inherited by nested classes, so the
     * rules look at the outermost class.
     */
    static JavaClass outermostClass(JavaClass javaClass) {
        JavaClass current = javaClass;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }

    /** True for a class and for everything nested in it, when the outermost class carries the annotation. */
    static DescribedPredicate<JavaClass> outermostClassIsAnnotatedWith(Class<? extends Annotation> annotation) {
        return DescribedPredicate.describe(
                "belong to a class annotated with @" + annotation.getSimpleName(),
                javaClass -> outermostClass(javaClass).isAnnotatedWith(annotation));
    }
}