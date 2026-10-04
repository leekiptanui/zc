/**
 * Population and Cohort (roadmap milestone M6): cohorts, identification runs (implausible
 * volumes are held for human review) and cohort membership. Membership is eligibility for
 * invitation; it is neither enrolment nor consent.
 *
 * <p>Owns {@code zc_cohort}, {@code zc_cohort_run} and {@code zc_cohort_membership}, created
 * by {@code db/changelog/migrations/20260929_06_cohort.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/cohort/}.
 */
package com.zimasahealth.zcare.domains.cohort;
