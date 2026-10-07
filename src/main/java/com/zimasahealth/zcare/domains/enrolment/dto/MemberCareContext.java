package com.zimasahealth.zcare.domains.enrolment.dto;

import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.domains.reference.dto.MemberView;

/**
 * A member's care context (04B section 11). The header (member, enrolment, consent) is always
 * returned; {@code healthContext} only when consent in force covers health content.
 */
public record MemberCareContext(MemberView member, EnrolmentView enrolment, Consent consent,
                                Map<String, Object> healthContext) {

    public record Consent(String state, String wordingVersion, List<String> scope) {
    }
}
