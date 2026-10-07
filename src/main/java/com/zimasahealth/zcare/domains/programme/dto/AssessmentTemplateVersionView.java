package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

public record AssessmentTemplateVersionView(Long id, Long templateId, Integer versionNumber, String status,
                                            Map<String, Object> questionnaire, Map<String, Object> scoringRules) {
}
