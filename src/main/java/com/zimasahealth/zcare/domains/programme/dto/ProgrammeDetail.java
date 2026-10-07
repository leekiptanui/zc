package com.zimasahealth.zcare.domains.programme.dto;

import java.util.List;

public record ProgrammeDetail(Long id, String code, String name, String careModel, String status, String description,
                              List<ProgrammeVersionSummary> versions, ProgrammeVersionDetail latestVersion) {
}
