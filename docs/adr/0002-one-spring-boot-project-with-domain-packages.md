# 0002. One Spring Boot project with domain packages

**Status:** Accepted · **Date:** 2026-09-29

## Context

The roadmap delivers ZCare in 20 milestones. M3 to M19 are each one business area, built server
and screens together and finished before the next begins. The project-layout standard asks for:

- feature packages plus fixed infrastructure packages (PKG-03, PKG-04);
- tables created by one migration file belonging to one feature (PKG-06);
- a lean root with a fixed set of top-level folders (ROOT-01, ROOT-03).

## Decision

ZCare is a single Maven project in the standard Spring Boot layout:

```
src/main/java/com/zimasahealth/zcare/
├── ZimasaZcareApplication.java    the only class in the base package (PKG-02)
├── config/ security/ tenant/ common/     cross-cutting infrastructure (PKG-04)
└── domains/<domain>/                     one package per milestone M3–M19
    ├── controllers/                      HTTP entry points, no business logic
    ├── services/                         all business logic
    ├── repositories/                     Spring Data data access
    ├── entities/                         JPA entities
    ├── dto/                              request and response types
    ├── mappers/                          entity ↔ DTO mapping
    └── specifications/                   JPA Specifications for dynamic queries
src/main/resources/
├── application*.yml
├── db/changelog/                         the Liquibase changelog (DB-02)
└── domains/<domain>/                     that domain's non-Java files
src/test/java, src/test/resources          mirror main (TST-01)
```

Rules:

1. Each business domain is one package, `com.zimasahealth.zcare.domains.<domain>`, from `access`
   (M3) to `ai` (M19). Inside it, every domain has the same seven role folders from the start:
   `controllers`, `services`, `repositories`, `entities`, `dto`, `mappers` and `specifications`.
   Classes carry role suffixes (NAM): `EnrolmentController`, `EnrolmentService`,
   `EnrolmentRepository`, `Enrolment`, `CreateEnrolmentRequest` and `EnrolmentResponse`,
   `EnrolmentMapper`, `EnrolmentSpecifications`.

   This deviates from PKG-05, which keeps a feature flat until about 15 classes. The fixed role
   folders were chosen so that every domain reads the same way. PKG-03 still holds: there are no
   top-level `controllers`, `services` or `repositories` packages shared across domains.
2. **All business logic lives in `services`.** Calls flow one way:
   - controllers call services only;
   - services call repositories, specifications and mappers, and return DTOs;
   - entities never leave the service layer, and controllers never touch a repository or an
     entity (PKG-09).

   Repositories, mappers and specifications hold no business rules.
3. A domain package owns exactly the tables of its migration file. Its `entities` map those tables
   and no others. The mapping is in [overview.md](../architecture/overview.md) and each domain's
   `package-info.java`.
4. A domain's non-Java files, such as message templates and programme content, live in
   `src/main/resources/domains/<domain>/`. Liquibase migrations stay in `db/changelog/` because
   DB-02 requires one changelog location, and each migration file names its owning domain in its
   header.
5. `config`, `security`, `tenant` and `common` never depend on a domain package. Domain packages may
   depend on them (PKG-04). A domain may use another domain's services and DTOs only without
   creating a cycle, and never another domain's repositories or entities. Effects that need no
   compile-time link travel as domain events through the transactional outbox.
6. Structural tests (ArchUnit) enforce rules 2 and 5 from milestone M2, before the first domain
   code lands.

## Alternatives considered

A Maven module per milestone domain was tried and rejected on 2026-09-29. The standard single
project keeps the layout familiar, keeps the root lean without extra top-level folders, and
leaves module boundaries to package rules that tests enforce.

## Consequences

- One build, one artifact, one `application.yml`. Adding a domain's code never touches build files.
- Package boundaries are not enforced by the compiler, so the ArchUnit rules from M2 carry that
  weight and must be in place before the first domain code lands.
- Each domain package and its role folders exist before its milestone. Until then they hold only
  `package-info.java` files, which record the domain's owned tables and each folder's role.
- Entities, mappers and specifications imply Spring Data JPA (schema validated, never generated:
  DB-01), a mapping library such as MapStruct (BLD-08) and JPA Specifications. Each dependency is
  added with the first code that uses it (BLD-07).
