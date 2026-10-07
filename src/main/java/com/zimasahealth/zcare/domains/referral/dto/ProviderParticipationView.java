package com.zimasahealth.zcare.domains.referral.dto;

import java.time.Instant;

public record ProviderParticipationView(Long id, Long programmeVersionId, Long organisationId, String status,
                                        String agreementRef, Instant joinedAt) {
}
