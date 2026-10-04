# Migration plan

File by file, what the changelog creates and which domain package owns it (PKG-06). Files run in `NN`
order. Every table file creates its tables, then their foreign keys, triggers, row-level security
and grants. "Design source" refers to the agreed design, `db.sql` (ADR-0004).

| NN | File | Owning package (milestone) | Creates | Design source |
|---|---|---|---|---|
| 01 | `20260929_01_database_functions.xml` | database-wide (M1) | Extension `btree_gist`; functions `zc_current_tenant`, `zc_touch_row`, `zc_forbid_change`, `zc_forbid_pinned_change`, `zc_forbid_published_version_change` | V001 preamble |
| 02 | `20260929_02_database_roles.xml` | database-wide (M1) | Roles `zc_app`, `zc_housekeeping`; schema `public` privileges | V002 |
| 03 | `20260929_03_access.xml` | `domains.access` (M3) | `zc_tenant`, `zc_organisation`, `zc_role_assignment`, `zc_break_glass_grant`, `zc_config_history`, `zc_user`, `zc_tenant_whatsapp` | V001 §01, V004, V005 |
| 04 | `20260929_04_external_reference.xml` | `domains.reference` (M5) | `zc_member_reference`, `zc_external_reference` | V001 §02 |
| 05 | `20260929_05_programme.xml` | `domains.programme` (M4) | `zc_programme`, `zc_programme_version`, `zc_assessment_template`, `zc_assessment_template_version`, `zc_gap_rule`, `zc_task_template`, `zc_referral_type`, `zc_observation_type`, `zc_goal_type`, `zc_outcome_definition` | V001 §03, §3a |
| 06 | `20260929_06_cohort.xml` | `domains.cohort` (M6) | `zc_cohort`, `zc_cohort_run`, `zc_cohort_membership` | V001 §04 |
| 07 | `20260929_07_enrolment.xml` | `domains.enrolment` (M7) | `zc_enrolment`, `zc_consent_record`, `zc_member_preference` | V001 §05 |
| 08 | `20260929_08_assessment.xml` | `domains.assessment` (M8) | `zc_assessment`, `zc_assessment_response`, `zc_risk_classification` | V001 §06 |
| 09 | `20260929_09_careplan.xml` | `domains.careplan` (M9) | `zc_care_plan`, `zc_goal`, `zc_intervention`, `zc_care_plan_link`, `zc_plan_review`; function `zc_care_plan_approval_immutable` | V001 §07 |
| 10 | `20260929_10_carework.xml` | `domains.carework` (M10) | `zc_task`, `zc_task_dependency`, `zc_task_assignment_history`, `zc_work_queue_definition`; function `zc_task_dependency_gate` | V001 §08 |
| 11 | `20260929_11_referral.xml` | `domains.referral` (M11) | `zc_referral`, `zc_transition`, `zc_provider_participation`, `zc_provider_action` | V001 §09 |
| 12 | `20260929_12_medication.xml` | `domains.medication` (M12) | `zc_medication_coordination`, `zc_refill_request`, `zc_adherence_event` | V001 §10 |
| 13 | `20260929_13_observation.xml` | `domains.observation` (M13) | `zc_monitoring_schedule`, `zc_observation`; function `zc_observation_correction_only` | V001 §11 |
| 14 | `20260929_14_caregap.xml` | `domains.caregap` (M14) | `zc_care_gap` | V001 §12 |
| 15 | `20260929_15_engagement.xml` | `domains.engagement` (M15) | `zc_outreach_request`, `zc_outreach_delivery`, `zc_inbound_message` | V001 §13, corrected V003 |
| 16 | `20260929_16_outcome.xml` | `domains.outcome` (M16) | `zc_outcome_observation`, `zc_report_snapshot` | V001 §14 |
| 17 | `20260929_17_ai.xml` | `domains.ai` (M19) | `zc_ai_recommendation`, `zc_ai_review` | V001 §15 |
| 18 | `20260929_18_audit.xml` | `domains.audit` (M17) | `zc_domain_audit`, `zc_security_event` | V001 §16, V004 (login event types) |
| 19 | `20260929_19_integration.xml` | `domains.integration` (M18) | `zc_idempotency_key`, `zc_integration_outbox`, `zc_integration_inbox`, `zc_dead_letter` | V001 §17 |
| 20 | `20260929_20_security_posture_checks.xml` | database-wide (M1) | Nothing: read-only checks run on every update | V002 sanity checks |

The files follow the agreed design's section order, which is also dependency order: every
foreign key points at a table created in the same file or an earlier one. That is why the files
are not in milestone order: AI (file 17, M19) precedes audit (file 18, M17), as in the design.
