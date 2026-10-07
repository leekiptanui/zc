package com.zimasahealth.zcare.domains.enrolment.dto;

import java.util.List;

/**
 * @param requiredWordingVersion  the wording the programme version requires, null when unset
 * @param privacyApprovalPending  true while Privacy has not approved the assisted script, which
 *                                keeps an assisted capture from validating (UC-ZC-001 V2; OD-30)
 */
public record ConsentView(Long consentRecordId, Long enrolmentId, String consentState, String wordingVersion,
                          String requiredWordingVersion, String channel, List<String> scopeContentClasses,
                          boolean privacyApprovalPending) {
}
