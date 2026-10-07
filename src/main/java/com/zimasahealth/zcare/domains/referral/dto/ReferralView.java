package com.zimasahealth.zcare.domains.referral.dto;

import java.time.Instant;

public record ReferralView(Long id, Long enrolmentId, Long memberId, Long referralTypeId, Long receivingOrgId,
                           String receivingProviderRef, String status, String reason, Instant routedAt,
                           Instant respondedAt, String declineReason, Instant completedAt, String confirmedBy,
                           String confirmationSource, String evidenceRef, String cancelReason) {
}
