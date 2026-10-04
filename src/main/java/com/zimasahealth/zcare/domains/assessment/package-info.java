/**
 * Assessment and Risk (roadmap milestone M8): assessments pinned to a template version,
 * append-only responses and append-only priority classification. A priority tier is programme
 * priority, never a diagnosis.
 *
 * <p>Owns {@code zc_assessment}, {@code zc_assessment_response} and
 * {@code zc_risk_classification}, created by
 * {@code db/changelog/migrations/20260929_08_assessment.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/assessment/}.
 */
package com.zimasahealth.zcare.domains.assessment;
