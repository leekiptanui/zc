package com.zimasahealth.zcare.domains.programme.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProgrammeVersionDetail(Long id, Integer versionNumber, String status, Map<String, Object> content,
                                     String clinicalApprovedBy, Instant clinicalApprovedAt, Instant publishedAt,
                                     List<ObservationTypeView> observationTypes, List<GoalTypeView> goalTypes,
                                     List<GapRuleView> gapRules, List<TaskTemplateView> taskTemplates,
                                     List<OutcomeDefinitionView> outcomeMeasures) {
}
