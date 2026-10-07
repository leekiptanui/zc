package com.zimasahealth.zcare;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zimasahealth.zcare.common.api.AuditOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Package boundaries the compiler cannot enforce (ADR-0002 rule 6; M02-22). */
class ArchitectureTest {

    private static final String BASE = "com.zimasahealth.zcare";
    private static final List<String> DOMAINS = List.of("access", "programme", "reference", "cohort", "enrolment",
            "assessment", "careplan", "carework", "referral", "medication", "observation", "caregap", "engagement",
            "outcome", "audit", "integration", "ai");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void controllersNeverTouchRepositoriesOrEntities() {
        noClasses().that().resideInAPackage("..domains..controllers..")
                .should().dependOnClassesThat().resideInAnyPackage("..repositories..", "..entities..")
                .check(classes);
    }

    @Test
    void noDomainUsesAnotherDomainsRepositoriesOrEntities() {
        for (String domain : DOMAINS) {
            String own = BASE + ".domains." + domain + "..";
            noClasses().that().resideInAPackage(own)
                    .should().dependOnClassesThat(otherDomainPersistence(domain))
                    .check(classes);
        }
    }

    @Test
    void infrastructureNeverDependsOnADomain() {
        noClasses().that().resideInAnyPackage(BASE + ".common..", BASE + ".config..", BASE + ".security..",
                        BASE + ".tenant..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".domains..")
                .check(classes);
    }

    @Test
    void everyEndpointIsAuthorisedAndNamesItsAuditOperation() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and().areAnnotatedWith(GetMapping.class).or().areAnnotatedWith(PostMapping.class)
                .should().beAnnotatedWith(PreAuthorize.class)
                .andShould().beAnnotatedWith(AuditOperation.class)
                .check(classes);
    }

    @Test
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class).check(classes);
    }

    @Test
    void nothingWritesToStandardOutput() {
        noClasses().should().accessField(System.class, "out").orShould().accessField(System.class, "err")
                .check(classes);
    }

    private static DescribedPredicate<JavaClass> otherDomainPersistence(String domain) {
        return new DescribedPredicate<>("another domain's repositories or entities") {
            @Override
            public boolean test(JavaClass target) {
                String name = target.getPackageName();
                if (!name.startsWith(BASE + ".domains.") || name.startsWith(BASE + ".domains." + domain + ".")) {
                    return false;
                }
                return name.endsWith(".repositories") || name.endsWith(".entities");
            }
        };
    }
}
