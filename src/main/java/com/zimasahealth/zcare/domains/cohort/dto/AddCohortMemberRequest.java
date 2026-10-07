package com.zimasahealth.zcare.domains.cohort.dto;

import com.zimasahealth.zcare.domains.reference.dto.MemberIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A manual inclusion: an existing member by {@code memberId}, or a member identified at source by
 * {@code member}, whose reference is created if ZCare has not seen them before. A manual
 * inclusion always carries its reason (UC-ZC-011 3a).
 */
public record AddCohortMemberRequest(
        Long memberId,
        @Valid MemberIdentity member,
        @NotBlank @Size(max = 1000) String inclusionReason) {
}
