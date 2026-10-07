package com.zimasahealth.zcare.domains.programme.dto;

import java.time.Instant;

public record ProgrammeVersionSummary(Long id, Integer versionNumber, String status, boolean clinicallyApproved,
                                      Instant publishedAt) {
}
