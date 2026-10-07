package com.zimasahealth.zcare.domains.observation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ObservationView(Long id, Long enrolmentId, Long observationTypeId, BigDecimal valueNumeric, String unit,
                              Instant observedAt, String source, String sourceRef, String loincCode,
                              boolean invalidated, String invalidationReason) {
}
