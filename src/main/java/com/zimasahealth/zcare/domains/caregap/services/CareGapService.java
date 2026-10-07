package com.zimasahealth.zcare.domains.caregap.services;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
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
import com.zimasahealth.zcare.domains.caregap.dto.CareGapActionRequest;
import com.zimasahealth.zcare.domains.caregap.dto.CareGapView;
import com.zimasahealth.zcare.domains.caregap.dto.RecordCareGapRequest;
import com.zimasahealth.zcare.domains.caregap.entities.CareGap;
import com.zimasahealth.zcare.domains.caregap.mappers.CareGapMapper;
import com.zimasahealth.zcare.domains.caregap.repositories.CareGapRepository;
import com.zimasahealth.zcare.domains.caregap.specifications.CareGapSpecifications;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.programme.dto.GapRuleView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Care gaps (04A section 7.10; UC-ZC-005). Detection deduplicates on the gap key, so running a
 * check twice never creates a second gap (ZCARE_GAP_DUPLICATE). Suppressing or closing needs an
 * authorised role and a reason; the two failures are different codes (UC-ZC-005 E2 and E3).
 * Who may suppress and who may close is still open (OD-21); until it is decided, both are
 * allowed to care managers and programme administrators.
 */
@Service
@Transactional
public class CareGapService {

    static final List<String> OPEN = List.of("detected", "assigned");
    private static final Set<ZcareRole> AUTHORISED = Set.of(ZcareRole.CARE_MANAGER, ZcareRole.PROGRAMME_ADMIN);

    private final CareGapRepository gaps;
    private final CareGapMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ProgrammeQueryService programmes;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public CareGapService(CareGapRepository gaps, CareGapMapper mapper, EnrolmentQueryService enrolments,
                          ProgrammeQueryService programmes, ConsentGate consentGate, AuditWriter audit,
                          OutboxWriter outbox) {
        this.gaps = gaps;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.programmes = programmes;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public PagedResponse<CareGapView> list(String status, Long enrolmentId, String gapType, PageParams params) {
        Specification<CareGap> filter = Specification.where(CareGapSpecifications.hasStatus(status))
                .and(CareGapSpecifications.forEnrolment(enrolmentId))
                .and(CareGapSpecifications.ofType(gapType));
        return PagedResponse.of(gaps.findAll(filter, params.toPageable(Sort.by(Sort.Direction.DESC, "detectedAt"))),
                params, mapper::toView);
    }

    @Transactional(readOnly = true)
    public List<CareGapView> openForEnrolment(long enrolmentId) {
        return gaps.findByEnrolmentIdAndStatusIn(enrolmentId, OPEN).stream().map(mapper::toView).toList();
    }

    @Transactional(readOnly = true)
    public long count(Collection<Long> programmeVersionIds, Collection<String> statuses) {
        return programmeVersionIds.isEmpty() ? 0 : gaps.countByProgrammeVersionIdInAndStatusIn(programmeVersionIds,
                statuses);
    }

    public ServiceResult<CareGapView> record(RecordCareGapRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        GapRuleView rule = programmes.activeGapRule(enrolment.programmeVersionId(), request.gapType())
                .orElseThrow(() -> BusinessException.invalid("gapType", "No active rule for gap type '"
                        + request.gapType() + "' in this programme version"));
        String key = CareGap.key(enrolment.memberId(), enrolment.programmeVersionId(), request.gapType(),
                request.periodKey());
        gaps.findFirstByGapKeyAndStatusIn(key, OPEN).ifPresent(existing -> {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_GAP_DUPLICATE,
                            "This gap is already open for the period; the detection was absorbed")
                    .with("existingCareGapId", existing.getId())
                    .data(mapper.toView(existing))
                    .session("careGapId", existing.getId());
        });
        CareGap gap = gaps.save(new CareGap(enrolment.id(), enrolment.memberId(), enrolment.programmeVersionId(),
                rule.id(), rule.gapType(), rule.ruleVersion(), request.periodKey(), request.detectReason(),
                request.evaluatedData(), Times.now(), request.dueOn()));
        outbox.emit(DomainEventType.CareGapDetected, "care_gap", gap.getId(),
                Map.of("careGapId", gap.getId(), "enrolmentId", enrolment.id(), "gapType", gap.getGapType(),
                        "ruleVersion", gap.getRuleVersion()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(CareGapOperations.DETECT_CARE_GAP, "care_gap", gap.getId())
                .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                .newState(Map.of("gapType", gap.getGapType(), "periodKey", gap.getPeriodKey(),
                        "ruleVersion", gap.getRuleVersion())));
        return ServiceResult.of(mapper.toView(gap)).next("assign_task", "suppress_gap", "close_gap")
                .session("careGapId", gap.getId()).session("enrolmentId", enrolment.id());
    }

    public ServiceResult<CareGapView> suppress(long careGapId, CareGapActionRequest request) {
        return action(careGapId, request, "suppressed", CareGapOperations.SUPPRESS_CARE_GAP);
    }

    public ServiceResult<CareGapView> close(long careGapId, CareGapActionRequest request) {
        return action(careGapId, request, "closed", CareGapOperations.CLOSE_CARE_GAP);
    }

    /** Open gaps end with the enrolment: closed as no longer pursuable. */
    public void closeOpen(long enrolmentId, String reason) {
        Instant now = Times.now();
        String actor = CurrentActor.idOrSystem();
        for (CareGap gap : gaps.findByEnrolmentIdAndStatusIn(enrolmentId, OPEN)) {
            gap.action("closed", "not_pursuable: " + reason, actor, now);
            audit.record(AuditEntry.of(RequestOperation.currentOr(CareGapOperations.CLOSE_CARE_GAP), "care_gap",
                    gap.getId()).member(gap.getMemberId()).newState(Map.of("status", "closed")).reason(reason));
        }
    }

    private ServiceResult<CareGapView> action(long careGapId, CareGapActionRequest request, String status,
                                              String operation) {
        Actor actor = CurrentActor.require();
        if (AUTHORISED.stream().noneMatch(actor::hasRole)) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_GAP_ACTION_RESTRICTED,
                            "Your role may not " + ("closed".equals(status) ? "close" : "suppress")
                                    + " care gaps; the request has been left for the role owner")
                    .with("careGapId", careGapId);
        }
        String reason = request == null ? null : request.reason();
        if (reason == null || reason.isBlank()) {
            throw "suppressed".equals(status)
                    ? BusinessException.of(ZCareExceptionCode.ZCARE_GAP_SUPPRESS_REASON_REQUIRED,
                    "A reason is required to suppress a gap").field("reason")
                    : BusinessException.required("reason", "A reason is required to close a gap manually");
        }
        CareGap gap = gaps.findById(careGapId).orElseThrow(() -> BusinessException.unknown("careGapId", "care gap"));
        if (!gap.isOpen()) {
            throw BusinessException.invalid("careGapId", "The gap is already " + gap.getStatus());
        }
        gap.action(status, reason, actor.id(), Times.now());
        if ("closed".equals(status)) {
            outbox.emit(DomainEventType.CareGapClosed, "care_gap", careGapId,
                    Map.of("careGapId", careGapId, "reason", reason), DataClassification.PHI);
        }
        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("status", status);
        if (request.evidence() != null) {
            newState.put("evidence", request.evidence());
        }
        audit.record(AuditEntry.of(operation, "care_gap", careGapId).member(gap.getMemberId())
                .programmeVersion(gap.getProgrammeVersionId()).newState(newState).reason(reason));
        return ServiceResult.of(mapper.toView(gap)).next("proceed").session("careGapId", careGapId);
    }
}
