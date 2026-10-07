package com.zimasahealth.zcare.domains.referral.dto;

import java.time.Instant;

public record ProviderActionView(Long id, Long participationId, String subjectType, Long subjectId, String actionCode,
                                 Instant actionAt, String actorRef, String evidenceRef) {
}
