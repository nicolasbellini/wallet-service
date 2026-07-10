package com.recargapay.walletservice.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Enforces the hexagonal boundaries documented in DESIGN.md as executable
 * rules instead of just code-review convention. Only src/main is analyzed
 * (test classes are exempt - e.g. field-injecting @Autowired MockMvc in
 * @SpringBootTest classes is the normal, correct way to write Spring tests).
 */
@AnalyzeClasses(packages = "com.recargapay.walletservice", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnFrameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "org.hibernate..")
            .because("the domain layer must stay framework-agnostic and unit-testable without a Spring context");

    @ArchTest
    static final ArchRule domainShouldNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapters..")
            .because("domain is the innermost hexagonal layer and must not depend on outer layers");

    @ArchTest
    static final ArchRule applicationShouldNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapters..")
            .because("application must depend only on domain ports, never on concrete adapter implementations");

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("this project uses constructor injection exclusively");
}
