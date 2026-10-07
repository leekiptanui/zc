package com.zimasahealth.zcare.domains.outcome.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OutcomeView(Long id, Long outcomeDefinitionId, String measureCode, Integer measureVersion,
                          Long enrolmentId, LocalDate periodStart, LocalDate periodEnd, BigDecimal valueNumeric,
                          BigDecimal numerator, BigDecimal denominator, boolean incomplete) {
}
