package com.zimasahealth.zcare.domains.cohort.dto;

/** A membership that may be invited: included, in a released cohort. */
public record InvitableMembership(Long membershipId, Long cohortId, Long memberId, Long programmeVersionId) {
}
