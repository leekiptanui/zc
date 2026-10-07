package com.zimasahealth.zcare.domains.programme.dto;

/** A thin list row: the programme and its latest version. */
public record ProgrammeSummary(Long id, String code, String name, String careModel, String status,
                               ProgrammeVersionSummary latestVersion) {
}
