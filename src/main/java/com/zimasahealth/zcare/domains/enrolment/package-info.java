/**
 * Enrolment, Consent and Preferences (roadmap milestone M7): the enrolment aggregate root, the
 * ZCare-owned consent record and member contact preferences. No sensitive workflow proceeds
 * without valid consent.
 *
 * <p>Owns {@code zc_enrolment}, {@code zc_consent_record} and {@code zc_member_preference},
 * created by {@code db/changelog/migrations/20260929_07_enrolment.xml}. Non-Java files of this
 * domain live in {@code src/main/resources/domains/enrolment/}.
 */
package com.zimasahealth.zcare.domains.enrolment;
