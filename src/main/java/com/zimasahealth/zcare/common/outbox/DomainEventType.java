package com.zimasahealth.zcare.common.outbox;

/**
 * The founding internal event set (04B section 12). Event names are the AsyncAPI contract's: no
 * call site invents its own.
 */
public enum DomainEventType {
    ProgrammePublished,
    ProgrammeRetired,
    CohortMemberIdentified,
    EnrolmentInvited,
    ConsentCaptured,
    ConsentValidated,
    ConsentRevoked,
    EnrolmentActivated,
    EnrolmentSuspended,
    EnrolmentWithdrawn,
    AssessmentCompleted,
    RiskClassificationChanged,
    CarePlanActivated,
    CarePlanRevised,
    TaskAssigned,
    TaskCompleted,
    TaskOverdue,
    ReferralCreated,
    ReferralAccepted,
    ReferralCompleted,
    RefillRequested,
    RefillFulfilled,
    ObservationCaptured,
    ThresholdReviewRequired,
    CareGapDetected,
    CareGapClosed,
    OutreachRequested,
    OutreachDelivered,
    OutreachFailed,
    OutreachCompleted,
    OutcomeRecorded,
    ProviderActionRecorded
}
