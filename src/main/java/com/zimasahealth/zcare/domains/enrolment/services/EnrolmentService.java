package com.zimasahealth.zcare.domains.enrolment.services;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentStatus;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.services.ConfigKeys;
import com.zimasahealth.zcare.domains.access.services.ConfigService;
import com.zimasahealth.zcare.domains.cohort.dto.InvitableMembership;
import com.zimasahealth.zcare.domains.cohort.services.CohortService;
import com.zimasahealth.zcare.domains.enrolment.dto.ActivateEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.CaptureConsentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.ConsentView;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentExitedEvent;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.dto.SuspendEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.dto.WithdrawEnrolmentRequest;
import com.zimasahealth.zcare.domains.enrolment.entities.ConsentRecord;
import com.zimasahealth.zcare.domains.enrolment.entities.Enrolment;
import com.zimasahealth.zcare.domains.enrolment.mappers.EnrolmentMapper;
import com.zimasahealth.zcare.domains.enrolment.repositories.ConsentRecordRepository;
import com.zimasahealth.zcare.domains.enrolment.repositories.EnrolmentRepository;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrolment, consent and exit (04A section 7.3; UC-ZC-001, UC-ZC-014). The rules held here:
 * <ul>
 *   <li>one live enrolment per member and programme (ZCARE_ENROLMENT_DUPLICATE);</li>
 *   <li>activation is a care manager's act, never automatic on consent, and needs validated
 *       consent to the programme's current wording plus a named responsible care manager;</li>
 *   <li>assisted consent validates only once Privacy has approved the script (OD-30);</li>
 *   <li>every exit carries a reason, and no open work outlives the enrolment.</li>
 * </ul>
 */
@Service
@Transactional
public class EnrolmentService {

    static final List<String> LIVE = List.of("invited", "active", "suspended");
    static final Set<String> EXIT_REASONS = Set.of("member_request", "consent_declined", "no_response",
            "eligibility_lost", "programme_completed", "clinical_direction", "administrative");
    /** Consent captured by the member on WhatsApp; any other channel is assisted capture. */
    static final String MEMBER_CHANNEL = "whatsapp";

    private final EnrolmentRepository enrolments;
    private final ConsentRecordRepository consents;
    private final ConsentGateService consentGate;
    private final EnrolmentMapper mapper;
    private final CohortService cohorts;
    private final ProgrammeQueryService programmes;
    private final ConfigService config;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final ApplicationEventPublisher events;

    public EnrolmentService(EnrolmentRepository enrolments, ConsentRecordRepository consents,
                            ConsentGateService consentGate, EnrolmentMapper mapper, CohortService cohorts,
                            ProgrammeQueryService programmes, ConfigService config, AuditWriter audit,
                            OutboxWriter outbox, ApplicationEventPublisher events) {
        this.enrolments = enrolments;
        this.consents = consents;
        this.consentGate = consentGate;
        this.mapper = mapper;
        this.cohorts = cohorts;
        this.programmes = programmes;
        this.config = config;
        this.audit = audit;
        this.outbox = outbox;
        this.events = events;
    }

    /** Invites a member from a released cohort, pinning the programme version (04A section 10.3). */
    public ServiceResult<EnrolmentView> invite(long cohortMembershipId) {
        InvitableMembership membership = cohorts.requireInvitable(cohortMembershipId);
        ProgrammeVersionView version = programmes.requirePublishedVersion(membership.programmeVersionId(),
                "cohortMembershipId");
        Optional<Enrolment> live = enrolments.findFirstByMemberIdAndProgrammeIdAndStatusIn(membership.memberId(),
                version.programmeId(), LIVE);
        if (live.isPresent()) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_ENROLMENT_DUPLICATE,
                            "This member already has a live enrolment in this programme; open the existing record")
                    .with("existingEnrolmentId", live.get().getId())
                    .data(mapper.toView(live.get()))
                    .session("enrolmentId", live.get().getId());
        }
        Enrolment enrolment = enrolments.save(new Enrolment(membership.memberId(), version.programmeId(),
                version.id(), membership.membershipId(), Times.now()));
        outbox.emit(DomainEventType.EnrolmentInvited, "enrolment", enrolment.getId(),
                Map.of("enrolmentId", enrolment.getId(), "memberId", enrolment.getMemberId(),
                        "programmeVersionId", enrolment.getProgrammeVersionId(),
                        "cohortMembershipId", cohortMembershipId),
                DataClassification.PHI);
        audit.record(AuditEntry.of(EnrolmentOperations.INVITE_MEMBER, "enrolment", enrolment.getId())
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .newState(Map.of("status", enrolment.getStatus())));
        return ServiceResult.of(mapper.toView(enrolment)).next("capture_consent", "view_member_context")
                .session("enrolmentId", enrolment.getId()).session("memberId", enrolment.getMemberId())
                .session("cohortMembershipId", cohortMembershipId)
                .session("programmeVersionId", enrolment.getProgrammeVersionId());
    }

    /**
     * Records consent. It validates at once only when the wording is the programme's current
     * wording and, for assisted capture, Privacy has approved the script; otherwise it is kept
     * as captured and the envelope says why (ZCARE_CONSENT_REQUIRED).
     */
    public ServiceResult<ConsentView> captureConsent(long enrolmentId, CaptureConsentRequest request) {
        Enrolment enrolment = requireEnrolment(enrolmentId);
        if (!LIVE.contains(enrolment.getStatus())) {
            throw BusinessException.invalid("enrolmentId", "Consent cannot be captured for a "
                    + enrolment.getStatus() + " enrolment").with("enrolmentState", enrolment.getStatus());
        }
        String[] scope = scopeCodes(request.scopeContentClasses());
        ProgrammeVersionView version = programmes.requireVersion(enrolment.getProgrammeVersionId(), "enrolmentId");
        String required = version.consentWordingVersion();
        boolean wordingCurrent = required == null || required.equals(request.wordingVersion());
        boolean assisted = !MEMBER_CHANNEL.equals(request.channel());
        boolean privacyApproved = !assisted || config.isEnabled(ConfigKeys.PRIVACY,
                ConfigKeys.ASSISTED_CONSENT_APPROVED);

        Instant now = Times.now();
        consents.findFirstByEnrolmentIdAndStatusIn(enrolmentId, ConsentGateService.IN_FORCE).ifPresent(previous -> {
            previous.supersede(now);
            consents.flush(); // only one record may be in force: retire the old before inserting the new
        });
        ConsentRecord record = new ConsentRecord(enrolmentId, enrolment.getMemberId(), request.wordingVersion(),
                request.channel(), scope, now, CurrentActor.require().id(), request.evidenceRef());
        boolean validated = wordingCurrent && privacyApproved;
        if (validated) {
            record.validate(CurrentActor.require().id(), now);
        }
        record = consents.save(record);

        Map<String, Object> payload = Map.of("enrolmentId", enrolmentId, "memberId", enrolment.getMemberId(),
                "channel", request.channel(), "wordingVersion", request.wordingVersion());
        outbox.emit(DomainEventType.ConsentCaptured, "enrolment", enrolmentId, payload, DataClassification.PHI);
        if (validated) {
            outbox.emit(DomainEventType.ConsentValidated, "enrolment", enrolmentId, payload, DataClassification.PHI);
        }
        audit.record(AuditEntry.of(EnrolmentOperations.CAPTURE_CONSENT, "consent_record", record.getId())
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .consent(request.wordingVersion(), request.channel())
                .newState(Map.of("status", record.getStatus(), "scope", List.of(scope))));

        ConsentView view = new ConsentView(record.getId(), enrolmentId, record.getStatus(), record.getWordingVersion(),
                required, record.getChannel(), List.of(scope), assisted && !privacyApproved);
        ServiceResult<ConsentView> result = ServiceResult.of(view)
                .session("enrolmentId", enrolmentId).session("consentRecordId", record.getId());
        if (validated) {
            return result.next("invited".equals(enrolment.getStatus()) ? "activate_enrolment" : "proceed");
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("enrolmentId", enrolmentId);
        context.put("consentState", record.getStatus());
        String message;
        if (!wordingCurrent) {
            context.put("capturedVersion", request.wordingVersion());
            context.put("requiredConsentVersion", required);
            message = "Consent was given to wording " + request.wordingVersion() + " but the programme requires "
                    + required + "; re-consent to the current wording is needed";
        } else {
            context.put("gate", ConfigKeys.PRIVACY + "/" + ConfigKeys.ASSISTED_CONSENT_APPROVED);
            message = "Assisted consent is recorded as captured only: Privacy has not approved the assisted"
                    + " script, so it cannot validate and the enrolment cannot activate on it";
        }
        return result.warning(ZCareExceptionCode.ZCARE_CONSENT_REQUIRED, message, context);
    }

    /** The consent-gate mutation: 04B section 20's worked example. */
    public ServiceResult<EnrolmentView> activate(long enrolmentId, ActivateEnrolmentRequest request) {
        Enrolment enrolment = requireEnrolment(enrolmentId);
        if (!"invited".equals(enrolment.getStatus())) {
            throw BusinessException.invalid("enrolmentId", "Only an invited enrolment can be activated; this one is "
                    + enrolment.getStatus()).with("enrolmentState", enrolment.getStatus());
        }
        ProgrammeVersionView version = programmes.requireVersion(enrolment.getProgrammeVersionId(), "enrolmentId");
        ConsentStatus consent;
        try {
            consent = consentGate.require(enrolmentId, ContentClass.HEALTH_CONTENT);
        } catch (BusinessException e) {
            throw e.data(stateOf(enrolment)).with("requiredConsentVersion", version.consentWordingVersion());
        }
        String required = version.consentWordingVersion();
        if (required != null && !required.equals(consent.wordingVersion())) {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_CONSENT_REQUIRED,
                            "Consent was given to outdated wording; re-consent to " + required + " is needed")
                    .with("enrolmentId", enrolmentId).with("consentState", consent.state().code())
                    .with("capturedVersion", consent.wordingVersion()).with("requiredConsentVersion", required)
                    .data(stateOf(enrolment)).session("enrolmentId", enrolmentId);
        }
        String careManager = request.responsibility().careManagerId().trim();
        enrolment.activate(careManager, Times.now());
        outbox.emit(DomainEventType.EnrolmentActivated, "enrolment", enrolmentId,
                Map.of("enrolmentId", enrolmentId, "memberId", enrolment.getMemberId(),
                        "programmeVersionId", enrolment.getProgrammeVersionId(), "careManagerId", careManager),
                DataClassification.PHI);
        audit.record(AuditEntry.of(EnrolmentOperations.ACTIVATE_ENROLMENT, "enrolment", enrolmentId)
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .previousState(Map.of("status", "invited"))
                .newState(Map.of("status", "active", "responsibleCm", careManager)));
        return ServiceResult.of(mapper.toView(enrolment)).next("create_care_plan", "assign_task")
                .session("enrolmentId", enrolmentId).session("memberId", enrolment.getMemberId())
                .session("programmeVersionId", enrolment.getProgrammeVersionId());
    }

    public ServiceResult<EnrolmentView> suspend(long enrolmentId, SuspendEnrolmentRequest request) {
        Enrolment enrolment = requireEnrolment(enrolmentId);
        if (!"active".equals(enrolment.getStatus())) {
            throw BusinessException.invalid("enrolmentId", "Only an active enrolment can be suspended; this one is "
                    + enrolment.getStatus());
        }
        enrolment.setStatus("suspended");
        outbox.emit(DomainEventType.EnrolmentSuspended, "enrolment", enrolmentId,
                Map.of("enrolmentId", enrolmentId, "reason", request.reason()), DataClassification.PHI);
        audit.record(AuditEntry.of(EnrolmentOperations.SUSPEND_ENROLMENT, "enrolment", enrolmentId)
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .previousState(Map.of("status", "active")).newState(Map.of("status", "suspended"))
                .reason(request.reason()));
        return ServiceResult.of(mapper.toView(enrolment)).next("resume_enrolment", "withdraw_enrolment")
                .session("enrolmentId", enrolmentId);
    }

    /** A suspended enrolment resumes only while consent is still in force. */
    public ServiceResult<EnrolmentView> resume(long enrolmentId) {
        Enrolment enrolment = requireEnrolment(enrolmentId);
        if (!"suspended".equals(enrolment.getStatus())) {
            throw BusinessException.invalid("enrolmentId", "Only a suspended enrolment can resume; this one is "
                    + enrolment.getStatus());
        }
        consentGate.require(enrolmentId, ContentClass.HEALTH_CONTENT);
        enrolment.setStatus("active");
        audit.record(AuditEntry.of(EnrolmentOperations.RESUME_ENROLMENT, "enrolment", enrolmentId)
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .previousState(Map.of("status", "suspended")).newState(Map.of("status", "active")));
        return ServiceResult.of(mapper.toView(enrolment)).next("proceed").session("enrolmentId", enrolmentId);
    }

    /**
     * Ends the enrolment. Open tasks, gaps, outreach and refills are closed in the same
     * transaction by the domains that own them; a member's own request also raises the
     * mandatory safe-exit follow-up.
     */
    public ServiceResult<EnrolmentView> withdraw(long enrolmentId, WithdrawEnrolmentRequest request) {
        Enrolment enrolment = requireEnrolment(enrolmentId);
        if (!LIVE.contains(enrolment.getStatus())) {
            throw BusinessException.invalid("enrolmentId", "The enrolment has already ended ("
                    + enrolment.getStatus() + ")");
        }
        if (!EXIT_REASONS.contains(request.reason())) {
            throw BusinessException.invalid("reason", "Unknown exit reason '" + request.reason() + "'")
                    .with("allowed", EXIT_REASONS.stream().sorted().toList());
        }
        if (request.revokeConsent() && (request.verbatim() == null || request.verbatim().isBlank())) {
            throw BusinessException.required("verbatim",
                    "Revoking consent records the member's own words; verbatim is required");
        }
        String previous = enrolment.getStatus();
        Instant now = Times.now();
        String exitStatus = "programme_completed".equals(request.reason()) ? "completed" : "withdrawn";
        enrolment.exit(exitStatus, request.reason(), now);

        boolean revoked = false;
        if (request.revokeConsent()) {
            Optional<ConsentRecord> inForce = consents.findFirstByEnrolmentIdAndStatusIn(enrolmentId,
                    ConsentGateService.IN_FORCE);
            if (inForce.isPresent()) {
                inForce.get().revoke("assisted", request.verbatim(), now);
                revoked = true;
                outbox.emit(DomainEventType.ConsentRevoked, "enrolment", enrolmentId,
                        Map.of("enrolmentId", enrolmentId, "memberId", enrolment.getMemberId(), "channel", "assisted"),
                        DataClassification.PHI);
            }
        }
        events.publishEvent(new EnrolmentExitedEvent(enrolmentId, enrolment.getMemberId(),
                enrolment.getResponsibleCm(), request.reason(), revoked));
        outbox.emit(DomainEventType.EnrolmentWithdrawn, "enrolment", enrolmentId,
                Map.of("enrolmentId", enrolmentId, "exitReason", request.reason(), "consentRevoked", revoked),
                DataClassification.PHI);
        audit.record(AuditEntry.of(EnrolmentOperations.WITHDRAW_ENROLMENT, "enrolment", enrolmentId)
                .member(enrolment.getMemberId()).programmeVersion(enrolment.getProgrammeVersionId())
                .previousState(Map.of("status", previous))
                .newState(Map.of("status", exitStatus, "consentRevoked", revoked))
                .reason(request.reason()));
        return ServiceResult.of(mapper.toView(enrolment)).next("proceed")
                .session("enrolmentId", enrolmentId).session("consentRevoked", revoked);
    }

    private Enrolment requireEnrolment(long enrolmentId) {
        return enrolments.findById(enrolmentId).orElseThrow(() -> BusinessException.unknown("enrolmentId", "enrolment"));
    }

    private static Map<String, Object> stateOf(Enrolment enrolment) {
        return Map.of("enrolmentId", enrolment.getId(), "state", enrolment.getStatus(),
                "programmeVersionId", enrolment.getProgrammeVersionId());
    }

    private static String[] scopeCodes(List<String> requested) {
        String[] scope = requested.stream().distinct().toArray(String[]::new);
        for (int i = 0; i < scope.length; i++) {
            if (ContentClass.fromCode(scope[i]).isEmpty()) {
                throw BusinessException.invalid("scopeContentClasses[" + i + "]", "Unknown content class '"
                        + scope[i] + "'").with("allowed", Arrays.stream(ContentClass.values())
                        .map(ContentClass::code).toList());
            }
        }
        return scope;
    }
}
