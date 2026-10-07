package com.zimasahealth.zcare.domains.programme.dto;

/** A questionnaire with its latest version and the version currently assignable, if any. */
public record AssessmentTemplateSummary(Long id, String code, String name, Integer latestVersionNumber,
                                        String latestVersionStatus, Long publishedVersionId) {
}
