package com.zimasahealth.zcare.common.consent;

/**
 * The one consent gate, called at command, dispatch and query time (04D section 9.4; M02-11).
 * Implemented by {@code domains.enrolment}, which owns consent records; every other domain
 * calls this interface and never reads consent tables itself.
 */
public interface ConsentGate {

    /** The enrolment's consent position. */
    ConsentStatus status(long enrolmentId);

    /**
     * Passes when consent in force covers the content class. Otherwise throws
     * ZCARE_CONSENT_REVOKED (HARD_STOP) when consent was withdrawn, or ZCARE_CONSENT_REQUIRED
     * (ESCALATE) in every other case.
     */
    ConsentStatus require(long enrolmentId, ContentClass contentClass);
}
