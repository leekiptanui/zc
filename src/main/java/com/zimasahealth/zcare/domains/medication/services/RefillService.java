package com.zimasahealth.zcare.domains.medication.services;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.RequestOperation;
import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.medication.dto.CreateRefillRequest;
import com.zimasahealth.zcare.domains.medication.dto.RecordFulfilmentRequest;
import com.zimasahealth.zcare.domains.medication.dto.RefillView;
import com.zimasahealth.zcare.domains.medication.entities.MedicationCoordination;
import com.zimasahealth.zcare.domains.medication.entities.RefillRequest;
import com.zimasahealth.zcare.domains.medication.mappers.RefillMapper;
import com.zimasahealth.zcare.domains.medication.repositories.MedicationCoordinationRepository;
import com.zimasahealth.zcare.domains.medication.repositories.RefillRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refill coordination (04A section 7.8; UC-ZC-004; DEC-006 pilot). PRD section 8.9 in code:
 * fulfilment is never "confirmed" without an authorised source. A care manager records what the
 * member reported; only a provider user's call can confirm, as source {@code provider}. Pharmacy
 * confirmation arrives with the pharmacy integration.
 */
@Service
@Transactional
public class RefillService {

    private static final List<String> OPEN = List.of("requested", "routed", "in_progress");
    private static final Set<String> STATES = Set.of("member_reported", "confirmed", "not_fulfilled");

    private final MedicationCoordinationRepository coordinations;
    private final RefillRequestRepository refills;
    private final RefillMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public RefillService(MedicationCoordinationRepository coordinations, RefillRequestRepository refills,
                         RefillMapper mapper, EnrolmentQueryService enrolments, ConsentGate consentGate,
                         AuditWriter audit, OutboxWriter outbox) {
        this.coordinations = coordinations;
        this.refills = refills;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    /** One open refill per medication: a second request opens the existing one (ZCARE_REFILL_DUPLICATE). */
    public ServiceResult<RefillView> create(CreateRefillRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        MedicationCoordination coordination = coordination(enrolment, request);
        Optional<RefillRequest> open = refills.findFirstByMedicationCoordinationIdAndStatusIn(coordination.getId(),
                OPEN);
        if (open.isPresent()) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_REFILL_DUPLICATE,
                            "An open refill request already exists for this medication; opening it")
                    .with("existingRefillRequestId", open.get().getId())
                    .data(mapper.toView(open.get()))
                    .session("refillRequestId", open.get().getId());
        }
        RefillRequest refill = refills.save(new RefillRequest(enrolment.id(), coordination.getId(), request.dueOn()));
        outbox.emit(DomainEventType.RefillRequested, "refill_request", refill.getId(),
                Map.of("refillRequestId", refill.getId(), "enrolmentId", enrolment.id(),
                        "medicationCoordinationId", coordination.getId()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(MedicationOperations.CREATE_REFILL_REQUEST, "refill_request", refill.getId())
                .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                .newState(Map.of("status", refill.getStatus())));
        return ServiceResult.of(mapper.toView(refill)).next("proceed")
                .session("refillRequestId", refill.getId())
                .session("medicationCoordinationId", coordination.getId());
    }

    public ServiceResult<RefillView> recordFulfilment(long refillId, RecordFulfilmentRequest request) {
        if (!STATES.contains(request.fulfilmentState())) {
            throw BusinessException.invalid("fulfilmentState", "Unknown fulfilment state '"
                    + request.fulfilmentState() + "'").with("allowed", STATES.stream().sorted().toList());
        }
        RefillRequest refill = refills.findById(refillId)
                .orElseThrow(() -> BusinessException.unknown("refillRequestId", "refill request"));
        if (!refill.isOpen()) {
            throw BusinessException.invalid("refillRequestId", "The refill request is " + refill.getStatus());
        }
        Actor actor = CurrentActor.require();
        String source = actor.primaryRole() == ZcareRole.PROVIDER_COORDINATOR ? "provider" : "member_reported";
        if (request.confirmationSource() != null && !request.confirmationSource().equals(source)) {
            throw BusinessException.invalid("confirmationSource", "The confirmation source is derived from the"
                    + " caller's role (" + source + ") and cannot be declared as another");
        }
        if ("confirmed".equals(request.fulfilmentState()) && "member_reported".equals(source)) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_FULFILMENT_SOURCE_REQUIRED,
                            "Fulfilment cannot be confirmed without a pharmacy or provider source; record what the"
                                    + " member reported instead")
                    .field("fulfilmentState").with("confirmationSource", "member_reported");
        }
        if ("member_reported".equals(request.fulfilmentState()) && !"member_reported".equals(source)) {
            throw BusinessException.invalid("fulfilmentState", "A provider records a confirmed or not-fulfilled"
                    + " outcome, not a member report");
        }
        consentGate.require(refill.getEnrolmentId(), ContentClass.HEALTH_CONTENT);
        refill.recordFulfilment(request.fulfilmentState(), source, actor.id(), Times.now());
        outbox.emit(DomainEventType.RefillFulfilled, "refill_request", refillId,
                Map.of("refillRequestId", refillId, "fulfilmentState", refill.getFulfilmentState(),
                        "confirmationSource", source),
                DataClassification.PHI);
        audit.record(AuditEntry.of(MedicationOperations.RECORD_FULFILMENT, "refill_request", refillId)
                .newState(Map.of("fulfilmentState", refill.getFulfilmentState(), "confirmationSource", source)));
        return ServiceResult.of(mapper.toView(refill)).next("proceed").session("refillRequestId", refillId);
    }

    /** Refill chasing stops when the enrolment ends (UC-ZC-014 section 3.9-4). */
    public void cancelOpen(long enrolmentId, String reason) {
        for (RefillRequest refill : refills.findByEnrolmentIdAndStatusIn(enrolmentId, OPEN)) {
            refill.cancel(reason);
            audit.record(AuditEntry.of(RequestOperation.currentOr(MedicationOperations.CANCEL_REFILL_REQUEST),
                    "refill_request", refill.getId()).newState(Map.of("status", "cancelled")).reason(reason));
        }
    }

    private MedicationCoordination coordination(EnrolmentView enrolment, CreateRefillRequest request) {
        if (request.medicationCoordinationId() != null) {
            MedicationCoordination existing = coordinations.findById(request.medicationCoordinationId())
                    .orElseThrow(() -> BusinessException.unknown("medicationCoordinationId", "medication coordination"));
            if (!existing.getEnrolmentId().equals(enrolment.id())) {
                throw BusinessException.invalid("medicationCoordinationId", "The medication belongs to another"
                        + " enrolment");
            }
            return existing;
        }
        if (request.planRef() == null || request.planRef().isBlank()) {
            throw BusinessException.required("planRef", "Give medicationCoordinationId, or planRef and planRefSystem");
        }
        if (request.planRefSystem() == null || request.planRefSystem().isBlank()) {
            throw BusinessException.required("planRefSystem", "planRefSystem names the system holding the plan");
        }
        return coordinations.save(new MedicationCoordination(enrolment.id(), request.planRef(),
                request.planRefSystem(), request.medicationDisplay(), LocalDate.now()));
    }
}
