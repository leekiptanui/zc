package com.zimasahealth.zcare.domains.programme.dto;

public record OutcomeDefinitionView(Long id, Long programmeVersionId, String measureCode, Integer measureVersion,
                                    String name, String category) {
}
