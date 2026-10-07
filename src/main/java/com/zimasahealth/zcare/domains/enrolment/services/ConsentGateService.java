package com.zimasahealth.zcare.domains.enrolment.services;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ConsentState;
import com.zimasahealth.zcare.common.consent.ConsentStatus;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.domains.enrolment.entities.ConsentRecord;
import com.zimasahealth.zcare.domains.enrolment.repositories.ConsentRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one consent gate (04D section 9.4; M07-10). The consent in force decides; with none in
 * force, the latest record does: revoked means the member withdrew (HARD_STOP, nothing may
 * follow), anything else means consent is still needed (ESCALATE to the care manager).
 */
@Service
@Transactional(readOnly = true)
public class ConsentGateService implements ConsentGate {

    static final List<String> IN_FORCE = List.of("captured", "validated", "scope_reduced");

    private final ConsentRecordRepository records;

    public ConsentGateService(ConsentRecordRepository records) {
        this.records = records;
    }

    @Override
    public ConsentStatus status(long enrolmentId) {
        Optional<ConsentRecord> inForce = records.findFirstByEnrolmentIdAndStatusIn(enrolmentId, IN_FORCE);
        if (inForce.isPresent()) {
            ConsentRecord record = inForce.get();
            return new ConsentStatus(enrolmentId, state(record.getStatus()), record.getWordingVersion(),
                    scope(record.getScopeContentClasses()));
        }
        return records.findFirstByEnrolmentIdOrderByCapturedAtDescIdDesc(enrolmentId)
                .map(latest -> new ConsentStatus(enrolmentId, state(latest.getStatus()), latest.getWordingVersion(),
                        Set.of()))
                .orElse(new ConsentStatus(enrolmentId, ConsentState.NONE, null, Set.of()));
    }

    @Override
    public ConsentStatus require(long enrolmentId, ContentClass contentClass) {
        ConsentStatus status = status(enrolmentId);
        if (status.state() == ConsentState.REVOKED) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_CONSENT_REVOKED,
                            "The member has withdrawn consent; no further action is possible on this enrolment")
                    .with("enrolmentId", enrolmentId).with("consentState", status.state().code())
                    .session("enrolmentId", enrolmentId);
        }
        if (!status.covers(contentClass)) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_CONSENT_REQUIRED,
                            "No valid consent covering " + contentClass.code() + " for enrolment " + enrolmentId)
                    .with("enrolmentId", enrolmentId).with("consentState", status.state().code())
                    .with("contentClass", contentClass.code())
                    .session("enrolmentId", enrolmentId);
        }
        return status;
    }

    private static ConsentState state(String status) {
        return ConsentState.valueOf(status.toUpperCase());
    }

    private static Set<ContentClass> scope(String[] codes) {
        return Arrays.stream(codes).map(ContentClass::fromCode).flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
