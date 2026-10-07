package com.zimasahealth.zcare.domains.cohort.dto;

public record CohortView(Long id, Long programmeVersionId, String code, String name, String status, String description,
                         long includedMembers) {
}
