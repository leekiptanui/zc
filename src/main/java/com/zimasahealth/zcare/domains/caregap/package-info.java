/**
 * Care Gaps (roadmap milestone M14): gaps raised by versioned programme rules, each carrying
 * its rule version, reason and evaluated data. One open gap per GapKey; closing or suppressing
 * one needs a reason.
 *
 * <p>Owns {@code zc_care_gap}, created by
 * {@code db/changelog/migrations/20260929_14_caregap.xml}. Non-Java files of this domain live
 * in {@code src/main/resources/domains/caregap/}.
 */
package com.zimasahealth.zcare.domains.caregap;
