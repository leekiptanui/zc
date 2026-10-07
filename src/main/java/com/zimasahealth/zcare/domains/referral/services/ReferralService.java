package com.zimasahealth.zcare.domains.referral.services;

import java.time.Instant;
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
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.services.OrganisationService;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.programme.dto.ReferralTypeView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import com.zimasahealth.zcare.domains.referral.dto.CreateReferralRequest;
import com.zimasahealth.zcare.domains.referral.dto.RecordReferralOutcomeRequest;
import com.zimasahealth.zcare.domains.referral.dto.ReferralView;
import com.zimasahealth.zcare.domains.referral.entities.Referral;
import com.zimasahealth.zcare.domains.referral.mappers.ReferralMapper;
import com.zimasahealth.zcare.domains.referral.repositories.ReferralRepository;
import com.zimasahealth.zcare.domains.referral.specifications.ReferralSpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Referrals (04A section 7.7; UC-ZC-007). The rule carried everywhere: ZCare records
 * coordination, never clinical truth. Completion needs provider or clinician confirmation, and
 * the confirmation source comes from the caller's authenticated role, never from the request.
 * A provider user acts only on referrals routed to their own organisation.
 */
@Service
@Transactional
public class ReferralService {

    static final List<String> OPEN = List.of("created", "routed", "accepted", "in_progress");
    private static final Set<String> OUTCOMES = Set.of("accepted", "declined", "in_progress", "completed",
            "cancelled");

    private final ReferralRepository referrals;
    private final ReferralMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ProgrammeQueryService programmes;
    private final OrganisationService organisations;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ReferralService(ReferralRepository referrals, ReferralMapper mapper, EnrolmentQueryService enrolments,
                           ProgrammeQueryService programmes, OrganisationService organisations,
                           ConsentGate consentGate, AuditWriter audit, OutboxWriter outbox) {
        this.referrals = referrals;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.programmes = programmes;
        this.organisations = organisations;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    /** Provider users see only referrals routed to their organisation. */
    @Transactional(readOnly = true)
    public PagedResponse<ReferralView> list(String status, Long enrolmentId, PageParams params) {
        Actor actor = CurrentActor.require();
        Long organisation = null;
        if (actor.primaryRole() == ZcareRole.PROVIDER_COORDINATOR) {
            organisation = requireOrganisation(actor);
        }
        Specification<Referral> filter = Specification.where(ReferralSpecifications.hasStatus(status))
                .and(ReferralSpecifications.forEnrolment(enrolmentId))
                .and(ReferralSpecifications.receivedBy(organisation));
        return PagedResponse.of(referrals.findAll(filter, params.toPageable(Sort.by(Sort.Direction.DESC, "id"))),
                params, mapper::toView);
    }

    public ServiceResult<ReferralView> create(CreateReferralRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        ReferralTypeView type = programmes.activeReferralType(request.referralTypeCode())
                .orElseThrow(() -> BusinessException.invalid("referralTypeCode", "Referral type '"
                        + request.referralTypeCode() + "' is not configured"));
        if (request.receivingOrgId() != null) {
            organisations.requireActive(request.receivingOrgId(), "receivingOrgId");
        }
        Referral referral = new Referral(enrolment.id(), enrolment.memberId(), type.id(), request.reason());
        referral.route(request.receivingOrgId(), request.receivingProviderRef(), Times.now());
        referral = referrals.save(referral);
        outbox.emit(DomainEventType.ReferralCreated, "referral", referral.getId(),
                Map.of("referralId", referral.getId(), "enrolmentId", enrolment.id(),
                        "referralTypeCode", type.code()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(ReferralOperations.CREATE_REFERRAL, "referral", referral.getId())
                .member(enrolment.memberId()).programmeVersion(enrolment.programmeVersionId())
                .organisation(referral.getReceivingOrgId())
                .newState(Map.of("status", referral.getStatus(), "referralTypeCode", type.code())));
        return ServiceResult.of(mapper.toView(referral)).next("proceed")
                .session("referralId", referral.getId()).session("enrolmentId", enrolment.id());
    }

    public ServiceResult<ReferralView> recordOutcome(long referralId, RecordReferralOutcomeRequest request) {
        if (!OUTCOMES.contains(request.outcome())) {
            throw BusinessException.invalid("outcome", "Unknown outcome '" + request.outcome() + "'")
                    .with("allowed", OUTCOMES.stream().sorted().toList());
        }
        Referral referral = referrals.findById(referralId)
                .orElseThrow(() -> BusinessException.unknown("referralId", "referral"));
        if (!OPEN.contains(referral.getStatus())) {
            throw BusinessException.invalid("referralId", "The referral is " + referral.getStatus());
        }
        Actor actor = CurrentActor.require();
        boolean provider = actor.primaryRole() == ZcareRole.PROVIDER_COORDINATOR;
        if (provider) {
            Long organisation = requireOrganisation(actor);
            if (referral.getReceivingOrgId() != null && !referral.getReceivingOrgId().equals(organisation)) {
                throw new AccessDeniedException("The referral is routed to another organisation");
            }
        }
        if (!"cancelled".equals(request.outcome()) && !"declined".equals(request.outcome())) {
            consentGate.require(referral.getEnrolmentId(), ContentClass.HEALTH_CONTENT);
        }
        String previous = referral.getStatus();
        Instant now = Times.now();
        if (request.receivingOrgId() != null) {
            organisations.requireActive(request.receivingOrgId(), "receivingOrgId");
        }
        referral.route(provider ? actor.organisationId() : request.receivingOrgId(), request.receivingProviderRef(),
                now);

        switch (request.outcome()) {
            case "accepted" -> {
                requireState(referral, List.of("routed"), "accepted");
                referral.accept(now);
                outbox.emit(DomainEventType.ReferralAccepted, "referral", referralId,
                        Map.of("referralId", referralId, "receivingOrgId", orZero(referral.getReceivingOrgId())),
                        DataClassification.PHI);
            }
            case "declined" -> {
                requireReason(request, "declining");
                requireState(referral, List.of("created", "routed", "accepted"), "declined");
                referral.decline(request.reason(), now);
            }
            case "in_progress" -> {
                requireState(referral, List.of("routed", "accepted"), "in progress");
                referral.start();
            }
            case "completed" -> {
                String source = switch (actor.primaryRole()) {
                    case PROVIDER_COORDINATOR -> "provider";
                    case CLINICIAN -> "clinician";
                    default -> throw BusinessException.invalid("outcome",
                                    "Completion needs provider or clinician confirmation; an unconfirmed report is"
                                            + " status, not closure")
                            .with("reason", "unconfirmed");
                };
                requireState(referral, List.of("routed", "accepted", "in_progress"), "completed");
                referral.complete(actor.id(), source, request.evidence(), now);
                outbox.emit(DomainEventType.ReferralCompleted, "referral", referralId,
                        Map.of("referralId", referralId, "confirmationSource", source), DataClassification.PHI);
            }
            default -> {
                requireReason(request, "cancelling");
                referral.cancel(request.reason());
            }
        }
        audit.record(AuditEntry.of(ReferralOperations.RECORD_REFERRAL_OUTCOME, "referral", referralId)
                .member(referral.getMemberId()).organisation(referral.getReceivingOrgId())
                .previousState(Map.of("status", previous)).newState(Map.of("status", referral.getStatus()))
                .reason(request.reason()));
        return ServiceResult.of(mapper.toView(referral)).next("proceed").session("referralId", referralId);
    }

    /** Open referrals end with the enrolment. */
    public void cancelOpen(long enrolmentId, String reason) {
        for (Referral referral : referrals.findByEnrolmentIdAndStatusIn(enrolmentId, OPEN)) {
            referral.cancel(reason);
            audit.record(AuditEntry.of(RequestOperation.currentOr(ReferralOperations.CANCEL_REFERRAL), "referral",
                    referral.getId()).member(referral.getMemberId()).newState(Map.of("status", "cancelled"))
                    .reason(reason));
        }
    }

    private static void requireState(Referral referral, List<String> allowed, String target) {
        if (!allowed.contains(referral.getStatus())) {
            throw BusinessException.invalid("outcome", "A " + referral.getStatus() + " referral cannot become "
                    + target + (referral.hasReceiver() ? "" : "; name a receiving organisation or provider first"));
        }
    }

    private static void requireReason(RecordReferralOutcomeRequest request, String action) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw BusinessException.required("reason", "A reason is required when " + action + " a referral");
        }
    }

    static Long requireOrganisation(Actor actor) {
        if (actor.organisationId() == null) {
            throw new AccessDeniedException("Provider tokens must carry their organisation");
        }
        return actor.organisationId();
    }

    private static Object orZero(Long value) {
        return value == null ? 0L : value;
    }
}
