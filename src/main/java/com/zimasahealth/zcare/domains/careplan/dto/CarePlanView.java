package com.zimasahealth.zcare.domains.careplan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CarePlanView(Long id, Long enrolmentId, Long programmeVersionId, Integer versionNumber, String status,
                           String summary, Long supersedesPlanId, Instant submittedAt, String approvedBy,
                           Instant approvedAt, Instant activatedAt, String rejectionReason, List<GoalView> goals,
                           List<InterventionView> interventions, List<ReviewView> reviews) {

    public record GoalView(Long id, Long goalTypeId, String description, BigDecimal targetValue, String targetUnit,
                           String targetCriteria, LocalDate targetDate, String status) {
    }

    public record InterventionView(Long id, Long goalId, String ownerRole, String description, String frequency,
                                   String status) {
    }

    public record ReviewView(Long id, String outcome, String reviewedBy, Instant reviewedAt, String notes) {
    }
}
