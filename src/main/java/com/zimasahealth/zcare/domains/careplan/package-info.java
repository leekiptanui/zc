/**
 * Care Planning (roadmap milestone M9): care plans, goals, interventions, plan links and the
 * append-only review history. Only a clinician activates a plan, and never the plan's author.
 *
 * <p>Owns {@code zc_care_plan}, {@code zc_goal}, {@code zc_intervention},
 * {@code zc_care_plan_link} and {@code zc_plan_review}, created by
 * {@code db/changelog/migrations/20260929_09_careplan.xml}. Non-Java files of this domain live
 * in {@code src/main/resources/domains/careplan/}.
 */
package com.zimasahealth.zcare.domains.careplan;
