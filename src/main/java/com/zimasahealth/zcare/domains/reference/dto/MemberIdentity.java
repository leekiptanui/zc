package com.zimasahealth.zcare.domains.reference.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Who a member is in the source system. The member number may cover a household; the individual
 * reference picks out the person (04D section 2.1).
 *
 * @param sourceDate the date the source data describes; defaults to today
 */
public record MemberIdentity(
        @NotBlank @Size(max = 64) String sourceSystem,
        @NotBlank @Size(max = 64) String sourceMemberNumber,
        @NotBlank @Size(max = 64) String sourceIndividualRef,
        @Size(max = 200) String displayName,
        @Pattern(regexp = "^\\+?[1-9][0-9]{6,14}$", message = "must be an international phone number")
        String contactMsisdn,
        LocalDate sourceDate) {
}
