# Architecture overview

ZCare coordinates care between clinic visits. It owns care-coordination records only:
programmes, enrolments, consent, care plans, tasks, referrals, care gaps, outreach and outcomes.
It references, and never copies as truth, the member master and claims (the payer system), the
clinical record and prescriptions (the provider) and dispensing (the pharmacy).

## Project layout

One Spring Boot project in the standard Maven layout
([ADR-0002](../adr/0002-one-spring-boot-project-with-domain-packages.md)):

```
src/main/java/com/zimasahealth/zcare/
├── config/ security/ tenant/ common/     cross-cutting infrastructure
└── domains/<domain>/                     business domains, one per milestone M3–M19
    ├── controllers/                      HTTP entry points, no business logic
    ├── services/                         all business logic
    ├── repositories/                     Spring Data data access
    ├── entities/                         JPA entities for the domain's own tables
    ├── dto/                              request and response types
    ├── mappers/                          entity ↔ DTO mapping
    └── specifications/                   JPA Specifications for dynamic queries
src/main/resources/
├── db/changelog/                         Liquibase changelog: every table, function, role and grant
└── domains/<domain>/                     each domain's non-Java files
src/test/java/com/zimasahealth/zcare/db/  schema tests, mirroring resources/db
```

## Packages

| Package under `com.zimasahealth.zcare` | Milestone | Owns |
|---|---|---|
| `config`, `security`, `tenant`, `common` | M2 | Cross-cutting infrastructure; no tables |
| `domains.access` | M3 | Tenants, organisations, role grants, break-glass grants, configuration history, local users, WhatsApp numbers |
| `domains.programme` | M4 | Programmes, versions, templates, gap rules, clinical vocabulary |
| `domains.reference` | M5 | Member and external-identifier references |
| `domains.cohort` | M6 | Cohorts, identification runs, membership |
| `domains.enrolment` | M7 | Enrolments, consent records, contact preferences |
| `domains.assessment` | M8 | Assessments, responses, priority classification |
| `domains.careplan` | M9 | Care plans, goals, interventions, review history |
| `domains.carework` | M10 | Tasks, dependencies, assignment history, work queues |
| `domains.referral` | M11 | Referrals, transitions, provider participation and actions |
| `domains.medication` | M12 | Medication coordination, refill requests, adherence events |
| `domains.observation` | M13 | Observations, monitoring schedules |
| `domains.caregap` | M14 | Care gaps |
| `domains.engagement` | M15 | Outreach requests and deliveries, inbound messages |
| `domains.outcome` | M16 | Outcome observations, report snapshots |
| `domains.audit` | M17 | Domain audit trail, security events |
| `domains.integration` | M18 | Idempotency keys, outbox, inbox, dead letters |
| `domains.ai` | M19 | AI recommendations and reviews (switched off) |

Table-level detail: [database/migration-plan.md](../database/migration-plan.md).

## Dependency rules

- **All business logic lives in `services`.** Controllers call services only. Services call
  repositories, specifications and mappers, and return DTOs. Entities never reach a controller,
  and a controller never touches a repository (PKG-09).
- `config`, `security`, `tenant` and `common` never depend on a domain package; domains may
  depend on them (PKG-04).
- A domain may use another domain's services and DTOs only without creating a cycle, and never
  another domain's repositories or entities. Effects that need no compile-time link travel as
  domain events through the transactional outbox.
- The base package holds nothing but `ZimasaZcareApplication` (PKG-02).

## Data isolation

Each client organisation (tenant) is isolated by PostgreSQL row-level security. The service
connects as `zc_app`, a role that owns nothing, deletes nothing and cannot switch row-level
security off. Each transaction sets `zcare.tenant_id`; without it, every tenant table reads
empty. Details and the exempt tables: [database/README.md](../database/README.md).
