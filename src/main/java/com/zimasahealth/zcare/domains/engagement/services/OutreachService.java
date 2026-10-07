package com.zimasahealth.zcare.domains.engagement.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ConsentState;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.context.RequestOperation;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.services.ConfigKeys;
import com.zimasahealth.zcare.domains.access.services.ConfigService;
import com.zimasahealth.zcare.domains.engagement.dto.CreateOutreachRequest;
import com.zimasahealth.zcare.domains.engagement.dto.OutreachView;
import com.zimasahealth.zcare.domains.engagement.entities.OutreachRequest;
import com.zimasahealth.zcare.domains.engagement.mappers.OutreachMapper;
import com.zimasahealth.zcare.domains.engagement.repositories.OutreachRequestRepository;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.reference.services.MemberReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outreach requests (04A section 7.11; UC-ZC-006). Withdrawn consent halts all contact at once,
 * whatever the content. Health content needs consent in force, and is held, never dropped, when
 * the frequency limit would be broken. The gate is checked again at dispatch by the channel
 * adapter (M15); this is the planner's check.
 */
@Service
@Transactional
public class OutreachService {

    /** Minimum hours between health messages until Privacy and Product set the value (UC-ZC-006 2a). */
    static final int DEFAULT_MIN_GAP_HOURS = 20;
    private static final List<String> CANCELLABLE = List.of("requested", "queued", "held");

    private final OutreachRequestRepository outreach;
    private final OutreachMapper mapper;
    private final MemberReferenceService members;
    private final EnrolmentQueryService enrolments;
    private final ConsentGate consentGate;
    private final ConfigService config;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public OutreachService(OutreachRequestRepository outreach, OutreachMapper mapper, MemberReferenceService members,
                           EnrolmentQueryService enrolments, ConsentGate consentGate, ConfigService config,
                           AuditWriter audit, OutboxWriter outbox) {
        this.outreach = outreach;
        this.mapper = mapper;
        this.members = members;
        this.enrolments = enrolments;
        this.consentGate = consentGate;
        this.config = config;
        this.audit = audit;
        this.outbox = outbox;
    }

    public ServiceResult<OutreachView> request(CreateOutreachRequest request) {
        ContentClass contentClass = ContentClass.fromCode(request.contentClass())
                .orElseThrow(() -> BusinessException.invalid("contentClass", "Unknown content class '"
                        + request.contentClass() + "'"));
        members.require(request.memberId(), "memberId");
        EnrolmentView enrolment = null;
        if (request.enrolmentId() != null) {
            enrolment = enrolments.require(request.enrolmentId(), "enrolmentId");
            if (!enrolment.memberId().equals(request.memberId())) {
                throw BusinessException.invalid("enrolmentId", "The enrolment belongs to another member");
            }
            if (consentGate.status(enrolment.id()).state() == ConsentState.REVOKED) {
                throw BusinessException.of(ZCareExceptionCode.ZCARE_CONSENT_REVOKED,
                        "The member has withdrawn consent; all contact has stopped").with("enrolmentId", enrolment.id());
            }
        }
        if (contentClass == ContentClass.HEALTH_CONTENT) {
            if (enrolment == null) {
                throw BusinessException.required("enrolmentId", "Health content needs the member's enrolment");
            }
            consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        }

        Instant now = Times.now();
        OutreachRequest outreachRequest = new OutreachRequest(request.memberId(),
                enrolment == null ? null : enrolment.id(), contentClass.code(), request.purpose(),
                request.templateRef(), now, request.scheduledFor());
        Instant nextPermissible = null;
        if (contentClass == ContentClass.HEALTH_CONTENT) {
            Instant last = outreach.lastHealthOutreach(request.memberId());
            int gapHours = config.intValue(ConfigKeys.OUTREACH, ConfigKeys.MIN_GAP_HOURS, DEFAULT_MIN_GAP_HOURS);
            if (last != null && last.plus(Duration.ofHours(gapHours)).isAfter(now)) {
                nextPermissible = last.plus(Duration.ofHours(gapHours));
                outreachRequest.hold("frequency_limit", nextPermissible);
            }
        }
        outreachRequest = outreach.save(outreachRequest);
        outbox.emit(DomainEventType.OutreachRequested, "outreach_request", outreachRequest.getId(),
                Map.of("outreachRequestId", outreachRequest.getId(), "memberId", request.memberId(),
                        "contentClass", contentClass.code(), "status", outreachRequest.getStatus()),
                contentClass == ContentClass.HEALTH_CONTENT ? DataClassification.PHI : DataClassification.NON_PHI);
        audit.record(AuditEntry.of(EngagementOperations.REQUEST_OUTREACH, "outreach_request", outreachRequest.getId())
                .member(request.memberId())
                .programmeVersion(enrolment == null ? null : enrolment.programmeVersionId())
                .newState(Map.of("status", outreachRequest.getStatus(), "contentClass", contentClass.code())));

        ServiceResult<OutreachView> result = ServiceResult.of(mapper.toView(outreachRequest)).next("proceed")
                .session("outreachRequestId", outreachRequest.getId()).session("memberId", request.memberId());
        if (nextPermissible != null) {
            result.warning(ZCareExceptionCode.ZCARE_OUTREACH_FREQUENCY_EXCEEDED,
                    "Held: a health message was sent within the frequency window; it will send at the next"
                            + " permissible time unless an authorised override releases it",
                    Map.of("nextPermissibleAt", nextPermissible.toString()));
        }
        return result;
    }

    /** Pending contact stops when the enrolment ends. */
    public void cancelPending(long enrolmentId, String reason) {
        for (OutreachRequest request : outreach.findByEnrolmentIdAndStatusIn(enrolmentId, CANCELLABLE)) {
            request.cancel(reason);
            audit.record(AuditEntry.of(RequestOperation.currentOr(EngagementOperations.CANCEL_OUTREACH),
                    "outreach_request", request.getId()).member(request.getMemberId())
                    .newState(Map.of("status", "cancelled")).reason(reason));
        }
    }
}
