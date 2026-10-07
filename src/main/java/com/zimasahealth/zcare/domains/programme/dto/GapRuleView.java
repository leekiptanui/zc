package com.zimasahealth.zcare.domains.programme.dto;

import java.util.Map;

public record GapRuleView(Long id, Long programmeVersionId, String gapType, Integer ruleVersion, String periodStrategy,
                          Map<String, Object> definition, String description, boolean active) {
}
