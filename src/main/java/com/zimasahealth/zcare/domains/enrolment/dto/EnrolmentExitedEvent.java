package com.zimasahealth.zcare.domains.enrolment.dto;

/**
 * Published in-process, inside the transaction that ended the enrolment. Domains holding open
 * work for the enrolment (tasks, gaps, outreach, refills) listen and close it in the same
 * transaction, so no open work outlives the enrolment. Listening needs no compile-time link
 * from enrolment to those domains (ADR-0002 rule 5).
 *
 * @param consentRevoked true when consent was withdrawn as part of the exit
 */
public record EnrolmentExitedEvent(long enrolmentId, long memberId, String responsibleCm, String exitReason,
                                   boolean consentRevoked) {

    /** The cancellation reason recorded on the work the exit closes. */
    public String cancelReason() {
        return consentRevoked ? "consent_revoked" : "enrolment_ended";
    }
}
