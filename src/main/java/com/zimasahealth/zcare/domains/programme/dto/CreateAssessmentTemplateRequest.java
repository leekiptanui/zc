package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A questionnaire and its draft version 1.
 *
 * @param questionnaire {@code {"questions": [{"code": ..., "text": ..., "required": true}]}}
 * @param scoringRules  {@code {"points": {question: {answer: points}}, "tiers": [{"tier": ..., "min": 8}, ...]}}:
 *                      the first tier whose {@code min} (and optional {@code max}) the score meets wins.
 *                      Tiers are a list because jsonb does not keep the order of object keys.
 */
public record CreateAssessmentTemplateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull Map<String, Object> questionnaire,
        Map<String, Object> scoringRules) {
}
