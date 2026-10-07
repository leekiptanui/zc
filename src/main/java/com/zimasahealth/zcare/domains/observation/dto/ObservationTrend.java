package com.zimasahealth.zcare.domains.observation.dto;

import java.util.List;

/** An enrolment's readings over time, oldest first; invalidated readings are kept and flagged. */
public record ObservationTrend(Long enrolmentId, String observationTypeCode, List<ObservationView> observations) {
}
