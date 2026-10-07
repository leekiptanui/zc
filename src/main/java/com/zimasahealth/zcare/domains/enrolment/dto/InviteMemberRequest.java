package com.zimasahealth.zcare.domains.enrolment.dto;

import jakarta.validation.constraints.NotNull;

/** @param cohortMembershipId an included membership of a released cohort */
public record InviteMemberRequest(@NotNull Long cohortMembershipId) {
}
