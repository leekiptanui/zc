package com.zimasahealth.zcare.domains.outcome.services;

import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.outcome.dto.OutcomeView;
import com.zimasahealth.zcare.domains.outcome.dto.RecordOutcomeRequest;
import com.zimasahealth.zcare.domains.outcome.entities.OutcomeObservation;
import com.zimasahealth.zcare.domains.outcome.repositories.OutcomeObservationRepository;
import com.zimasahealth.zcare.domains.programme.dto.OutcomeDefinitionView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outcome observations (04A section 7.12; UC-ZC-008). A value is recorded against the latest
 * version of a measure defined for the programme version; an undefined measure is refused, never
 * silently substituted (UC-ZC-008 E2).
 */
@Service
@Transactional
public class OutcomeService {

    private final OutcomeObservationRepository observations;
    private final ProgrammeQueryService programmes;
    private final EnrolmentQueryService enrolments;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public OutcomeService(OutcomeObservationRepository observations, ProgrammeQueryService programmes,
                          EnrolmentQueryService enrolments, ConsentGate consentGate, AuditWriter audit,
                          OutboxWriter outbox) {
        this.observations = observations;
        this.programmes = programmes;
        this.enrolments = enrolments;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    public ServiceResult<OutcomeView> record(RecordOutcomeRequest request) {
        programmes.requireVersion(request.programmeVersionId(), "programmeVersionId");
        OutcomeDefinitionView definition = programmes.outcomeDefinition(request.programmeVersionId(),
                        request.measureCode())
                .orElseThrow(() -> BusinessException.invalid("measureCode", "Measure '" + request.measureCode()
                        + "' is not defined for this programme version; no substitute is used"));
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw BusinessException.invalid("periodEnd", "periodEnd cannot be before periodStart");
        }
        boolean hasValue = request.value() != null || (request.numerator() != null && request.denominator() != null);
        String incompleteReason = request.incompleteReason() == null || request.incompleteReason().isBlank()
                ? null : request.incompleteReason();
        if (!hasValue && incompleteReason == null) {
            throw BusinessException.required("value",
                    "Give value, or numerator and denominator, or say why the data is incomplete");
        }
        EnrolmentView enrolment = null;
        if (request.enrolmentId() != null) {
            enrolment = enrolments.require(request.enrolmentId(), "enrolmentId");
            if (!enrolment.programmeVersionId().equals(request.programmeVersionId())) {
                throw BusinessException.invalid("enrolmentId", "The enrolment is on another programme version");
            }
            consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        }
        OutcomeObservation observation = observations.save(new OutcomeObservation(definition.id(),
                definition.measureVersion(), request.enrolmentId(), request.periodStart(), request.periodEnd(),
                request.value(), request.numerator(), request.denominator(), incompleteReason, Times.now()));
        outbox.emit(DomainEventType.OutcomeRecorded, "outcome_observation", observation.getId(),
                Map.of("outcomeObservationId", observation.getId(), "measureCode", definition.measureCode(),
                        "measureVersion", definition.measureVersion()),
                enrolment == null ? DataClassification.NON_PHI : DataClassification.PHI);
        audit.record(AuditEntry.of(OutcomeOperations.RECORD_OUTCOME, "outcome_observation", observation.getId())
                .member(enrolment == null ? null : enrolment.memberId())
                .programmeVersion(request.programmeVersionId())
                .newState(Map.of("measureCode", definition.measureCode(),
                        "measureVersion", definition.measureVersion(), "incomplete", observation.isIncomplete())));
        return ServiceResult.of(new OutcomeView(observation.getId(), definition.id(), definition.measureCode(),
                        definition.measureVersion(), observation.getEnrolmentId(), observation.getPeriodStart(),
                        observation.getPeriodEnd(), observation.getValueNumeric(), observation.getNumerator(),
                        observation.getDenominator(), observation.isIncomplete()))
                .next("proceed").session("outcomeObservationId", observation.getId());
    }
}
