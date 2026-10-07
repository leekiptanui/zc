package com.zimasahealth.zcare.domains.observation.services;

import java.time.Duration;
import java.time.Instant;
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
import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.carework.dto.SystemTask;
import com.zimasahealth.zcare.domains.carework.dto.TaskView;
import com.zimasahealth.zcare.domains.carework.services.TaskService;
import com.zimasahealth.zcare.domains.carework.services.TaskTypes;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.observation.dto.CaptureObservationRequest;
import com.zimasahealth.zcare.domains.observation.dto.ObservationResult;
import com.zimasahealth.zcare.domains.observation.dto.ObservationTrend;
import com.zimasahealth.zcare.domains.observation.dto.ObservationView;
import com.zimasahealth.zcare.domains.observation.entities.Observation;
import com.zimasahealth.zcare.domains.observation.mappers.ObservationMapper;
import com.zimasahealth.zcare.domains.observation.repositories.ObservationRepository;
import com.zimasahealth.zcare.domains.programme.dto.ObservationThreshold;
import com.zimasahealth.zcare.domains.programme.dto.ObservationTypeView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observation capture (04A section 7.9; UC-ZC-013). The type must belong to the enrolment's
 * programme version and the unit to the type. A reading beyond its threshold always raises a
 * clinician review; with no threshold configured the reading is stored but visibly not
 * evaluated, because silent non-evaluation is the dangerous failure (UC-ZC-013 E4).
 */
@Service
@Transactional
public class ObservationService {

    /** Clinician review SLA; SLA values are still unset (OD-29). */
    static final Duration REVIEW_SLA = Duration.ofHours(24);
    private static final Set<String> SOURCES = Set.of("member_reported", "care_team_recorded", "provider",
            "laboratory", "device");
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final ObservationRepository observations;
    private final ObservationMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ProgrammeQueryService programmes;
    private final TaskService tasks;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ObservationService(ObservationRepository observations, ObservationMapper mapper,
                              EnrolmentQueryService enrolments, ProgrammeQueryService programmes, TaskService tasks,
                              ConsentGate consentGate, AuditWriter audit, OutboxWriter outbox) {
        this.observations = observations;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.programmes = programmes;
        this.tasks = tasks;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    public ServiceResult<ObservationResult> capture(CaptureObservationRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        ObservationTypeView type = programmes.observationType(enrolment.programmeVersionId(),
                        request.observationTypeCode())
                .orElseThrow(() -> BusinessException.invalid("observationTypeCode", "Observation type '"
                        + request.observationTypeCode() + "' is not required by this programme version"));
        if (!type.unit().equals(request.unit())) {
            throw BusinessException.invalid("unit", "Unit '" + request.unit() + "' is not the configured unit '"
                    + type.unit() + "' for " + type.code());
        }
        if ((type.plausibleMin() != null && request.value().compareTo(type.plausibleMin()) < 0)
                || (type.plausibleMax() != null && request.value().compareTo(type.plausibleMax()) > 0)) {
            throw BusinessException.invalid("value", "The value is outside the plausible range for " + type.code())
                    .with("plausibleMin", type.plausibleMin()).with("plausibleMax", type.plausibleMax());
        }
        if (request.observedAt().isAfter(Times.now().plus(CLOCK_SKEW))) {
            throw BusinessException.invalid("observedAt", "An observation cannot be dated in the future");
        }
        validateSource(request.source());

        Observation observation = observations.save(new Observation(enrolment.id(), enrolment.memberId(),
                enrolment.programmeVersionId(), type.id(), request.value(), request.unit(), request.observedAt(),
                request.source(), request.sourceRef(), type.loincCode()));
        outbox.emit(DomainEventType.ObservationCaptured, "observation", observation.getId(),
                Map.of("observationId", observation.getId(), "enrolmentId", enrolment.id(),
                        "observationTypeCode", type.code(), "source", observation.getSource()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(ObservationOperations.CAPTURE_OBSERVATION, "observation", observation.getId())
                .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                .newState(Map.of("observationTypeCode", type.code(), "source", observation.getSource())));

        Optional<ObservationThreshold> threshold = programmes.threshold(enrolment.programmeVersionId(), type.code())
                .filter(ObservationThreshold::isEvaluable);
        ObservationView view = mapper.toView(observation);
        if (threshold.isEmpty()) {
            return ServiceResult.of(new ObservationResult(view, false, false, null))
                    .warning(ZCareExceptionCode.ZCARE_THRESHOLD_RULES_MISSING,
                            "Stored but not evaluated: no threshold is configured for " + type.code(),
                            Map.of("observationTypeCode", type.code()))
                    .session("observationId", observation.getId()).session("enrolmentId", enrolment.id());
        }
        if (!threshold.get().isBreachedBy(request.value())) {
            return ServiceResult.of(new ObservationResult(view, true, false, null)).next("view_member_context")
                    .session("observationId", observation.getId()).session("enrolmentId", enrolment.id());
        }
        TaskView review = tasks.raise(new SystemTask(enrolment.id(), enrolment.memberId(), TaskTypes.THRESHOLD_REVIEW,
                "Review " + type.name() + " reading", (short) 1, ZcareRole.CLINICIAN.code(),
                Times.now().plus(REVIEW_SLA), "observation", observation.getId()));
        outbox.emit(DomainEventType.ThresholdReviewRequired, "enrolment", enrolment.id(),
                Map.of("enrolmentId", enrolment.id(), "observationId", observation.getId(),
                        "observationTypeCode", type.code(), "reviewTaskId", review.id()),
                DataClassification.PHI);
        return ServiceResult.of(new ObservationResult(view, true, true, review.id()))
                .warning(ZCareExceptionCode.ZCARE_THRESHOLD_REVIEW_REQUIRED,
                        "The reading is beyond the configured threshold; a clinician review was raised. This is a"
                                + " review trigger, not a diagnosis",
                        Map.of("observationTypeCode", type.code(), "reviewTaskId", review.id()))
                .session("observationId", observation.getId()).session("enrolmentId", enrolment.id())
                .session("taskId", review.id());
    }

    /** A wrong reading is invalidated with its reason; it is never edited or deleted. */
    public ServiceResult<ObservationView> invalidate(long observationId, String reason) {
        Observation observation = observations.findById(observationId)
                .orElseThrow(() -> BusinessException.unknown("observationId", "observation"));
        if (observation.isInvalidated()) {
            throw BusinessException.invalid("observationId", "The observation is already invalidated");
        }
        observation.invalidate(CurrentActor.require().id(), reason, Times.now());
        audit.record(AuditEntry.of(ObservationOperations.INVALIDATE_OBSERVATION, "observation", observationId)
                .member(observation.getMemberId()).programmeVersion(observation.getProgrammeVersionId())
                .newState(Map.of("invalidated", true)).reason(reason));
        return ServiceResult.of(mapper.toView(observation)).next("proceed").session("observationId", observationId);
    }

    @Transactional(readOnly = true)
    public ObservationTrend trend(long enrolmentId, String observationTypeCode) {
        EnrolmentView enrolment = enrolments.require(enrolmentId, "enrolmentId");
        consentGate.require(enrolmentId, ContentClass.HEALTH_CONTENT);
        List<Observation> series;
        if (observationTypeCode == null) {
            series = observations.findByEnrolmentIdOrderByObservedAtAsc(enrolmentId);
        } else {
            ObservationTypeView type = programmes.observationType(enrolment.programmeVersionId(), observationTypeCode)
                    .orElseThrow(() -> BusinessException.invalid("type", "Unknown observation type '"
                            + observationTypeCode + "'"));
            series = observations.findByEnrolmentIdAndObservationTypeIdOrderByObservedAtAsc(enrolmentId, type.id());
        }
        return new ObservationTrend(enrolmentId, observationTypeCode, series.stream().map(mapper::toView).toList());
    }

    @Transactional(readOnly = true)
    public List<ObservationView> latest(long enrolmentId) {
        return observations.findTop10ByEnrolmentIdAndInvalidatedFalseOrderByObservedAtDesc(enrolmentId).stream()
                .map(mapper::toView).toList();
    }

    private static void validateSource(String source) {
        if (!SOURCES.contains(source)) {
            throw BusinessException.invalid("source", "Unknown source '" + source + "'")
                    .with("allowed", SOURCES.stream().sorted().toList());
        }
        Actor actor = CurrentActor.require();
        boolean provider = actor.primaryRole() == ZcareRole.PROVIDER_COORDINATOR;
        if (provider != "provider".equals(source)) {
            throw BusinessException.invalid("source", provider
                    ? "A provider user records readings with source 'provider'"
                    : "Source 'provider' is recorded only by provider users");
        }
    }
}
