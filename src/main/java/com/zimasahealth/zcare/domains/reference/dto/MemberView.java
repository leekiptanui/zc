package com.zimasahealth.zcare.domains.reference.dto;

import java.time.LocalDate;

/**
 * A member reference with its provenance: every copied detail shows its source and date, and
 * {@code authoritative} is always false (DEC-009).
 */
public record MemberView(Long id, String sourceSystem, String sourceMemberNumber, String sourceIndividualRef,
                         String displayName, String contactMsisdn, LocalDate sourceDate, boolean authoritative) {
}
