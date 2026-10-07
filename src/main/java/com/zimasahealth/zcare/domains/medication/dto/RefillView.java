package com.zimasahealth.zcare.domains.medication.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RefillView(Long id, Long enrolmentId, Long medicationCoordinationId, String status, LocalDate dueOn,
                         boolean overdue, String fulfilmentState, String confirmationSource,
                         Instant fulfilmentRecordedAt, String fulfilmentRecordedBy, Instant concludedAt,
                         String cancelReason) {
}
