# 0001. Technology stack and service identity

**Status:** Accepted · **Date:** 2026-09-29

## Context

ZCare is being rebuilt on a standard platform (Migration & Development Roadmap v0.3, 2026-09-28)
after a Python trial system. The project-layout standard (v1.0) requires one service name used
everywhere (ROOT-02) and asks for the first ADR to record the stack (DOC-06).

## Decision

| Concern | Decision |
|---|---|
| Language | Java 21 (`java.version`, compiled with `--release 21`) |
| Framework | Spring Boot 3.2.2, as the parent BOM; every managed version comes from it (BLD-03) |
| Build | Maven 3.9.16, pinned by the committed wrapper (BLD-09) |
| Database | PostgreSQL 16; the schema accepts 16 or later |
| Migrations | Liquibase, version managed by Spring Boot (4.24.x) |
| Sign-in | Keycloak, from milestone M2; ZCare will keep no passwords once it lands |
| Tests | JUnit, AssertJ and Testcontainers, all managed by Spring Boot |
| Web app | A separate project, not part of this repository |

**Service identity (ROOT-02):**

| Item | Value |
|---|---|
| Service ID, repository, `artifactId`, `spring.application.name`, Keycloak client, image | `zimasa-zcare-service` |
| Database name | `zimasa_zcare_service` (PostgreSQL identifiers take underscores) |
| `groupId` | `com.zimasahealth.zcare` |
| Base package | `com.zimasahealth.zcare` |
| Main class | `ZimasaZcareApplication` |

PKG-01 asks for `<reverse-domain>.<product>.<service>`. ZCare is a product with a single service,
so the product and service segments coincide and the base package is `com.zimasahealth.zcare`.
It is lowercase and starts with the `groupId`, which is what the rule protects.

## Consequences

- **Java 21 is a long-term-support release**, so the JDK does not force an upgrade on its own
  schedule, and libraries that rewrite bytecode (Byte Buddy, Lombok) support it.
- **Spring Boot 3.2 is past its open-source support window** (it ended November 2024), and 3.2.2
  is not the last 3.2 patch. Security fixes released after it, in Boot and in the libraries it
  manages, are not picked up. Moving to a supported Boot line is a later, deliberate decision.
- **Testcontainers 1.19, as managed by Boot 3.2.2, defaults to Docker API 1.32**, which Docker
  Engine 29 and later reject. The failsafe configuration in `pom.xml` sets `api.version` to 1.44
  so the integration tests run on current Docker Desktop.
- **Liquibase 4.24 is licensed under Apache 2.0.** Liquibase 5 moved to the Functional Source
  License (FSL-1.1-ALv2), so a later Boot upgrade that brings Liquibase 5 needs that licence
  accepted deliberately.
- Spring Boot upgrades move the managed versions of every library above together. Overrides go
  through Boot's own version properties, with a reason (BLD-03).
