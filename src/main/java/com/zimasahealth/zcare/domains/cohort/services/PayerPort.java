package com.zimasahealth.zcare.domains.cohort.services;

import java.time.LocalDate;
import java.util.List;

import com.zimasahealth.zcare.domains.reference.dto.MemberIdentity;

/**
 * The payer seam for cohort identification (ADR-004 section 3). The contract is condition- and
 * payer-agnostic: "members flagged with the programme's condition". A payer's own vocabulary
 * travels as {@code inclusionSourceDetail}, never as core enums.
 *
 * <p>Where ports live is still open (OD-09). DEC-022 made the payer push members to ZCare; this
 * pull remains for {@code /cohorts/{id}:identify} (04B section 10).
 */
public interface PayerPort {

    /**
     * @throws PayerUnavailableException when the payer cannot be reached; the run is recorded as
     *                                   failed and the caller told to retry
     */
    Identification identify(long programmeVersionId, String ruleReference);

    /** What the payer returned, and the date its data describes. */
    record Identification(List<IdentifiedMember> members, LocalDate sourceDataDate) {
    }

    /**
     * @param inclusionMethod {@code payer_flagged} or {@code claims_derived}
     */
    record IdentifiedMember(MemberIdentity identity, String inclusionMethod, String inclusionSourceDetail,
                            String inclusionReason) {
    }

    class PayerUnavailableException extends RuntimeException {

        public PayerUnavailableException(String message) {
            super(message);
        }
    }
}
