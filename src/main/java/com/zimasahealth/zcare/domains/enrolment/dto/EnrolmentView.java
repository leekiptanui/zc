package com.zimasahealth.zcare.domains.enrolment.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record EnrolmentView(Long id, Long memberId, Long programmeId, Long programmeVersionId, Long cohortMembershipId,
                            String status, Instant invitedAt, Instant activatedAt, String responsibleCm,
                            Instant exitedAt, String exitReason) {

    @JsonIgnore
    public boolean isActive() {
        return "active".equals(status);
    }

    /** Invited, active or suspended: the states that hold the one-live-enrolment slot. */
    @JsonIgnore
    public boolean isLive() {
        return "invited".equals(status) || "active".equals(status) || "suspended".equals(status);
    }
}
