package com.zimasahealth.zcare.domains.enrolment.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Consent captured on the member's behalf (assisted capture, UC-ZC-001).
 *
 * @param wordingVersion      the wording the member accepted
 * @param channel             how it was captured, for example {@code assisted_call}
 * @param scopeContentClasses {@code condition_neutral} and/or {@code health_content}
 * @param evidenceRef         where the evidence is kept, for example a call recording reference
 */
public record CaptureConsentRequest(
        @NotBlank @Size(max = 32) String wordingVersion,
        @NotBlank @Size(max = 32) String channel,
        @NotEmpty List<String> scopeContentClasses,
        @Size(max = 500) String evidenceRef) {
}
