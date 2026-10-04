/**
 * Programme Management (roadmap milestone M4): programmes and their immutable published
 * versions, assessment templates, gap rules, task templates and the programme-scoped clinical
 * vocabulary. A version is published only with recorded clinical approval.
 *
 * <p>Owns {@code zc_programme}, {@code zc_programme_version}, {@code zc_assessment_template},
 * {@code zc_assessment_template_version}, {@code zc_gap_rule}, {@code zc_task_template},
 * {@code zc_referral_type}, {@code zc_observation_type}, {@code zc_goal_type} and
 * {@code zc_outcome_definition}, created by
 * {@code db/changelog/migrations/20260929_05_programme.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/programme/}.
 */
package com.zimasahealth.zcare.domains.programme;
