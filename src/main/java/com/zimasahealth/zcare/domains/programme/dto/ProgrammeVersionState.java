package com.zimasahealth.zcare.domains.programme.dto;

/** Where a programme version stands after a command. */
public record ProgrammeVersionState(Long programmeId, Long programmeVersionId, Integer versionNumber, String state,
                                    boolean clinicallyApproved) {
}
