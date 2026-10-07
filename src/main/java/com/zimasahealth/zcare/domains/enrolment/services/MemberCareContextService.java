package com.zimasahealth.zcare.domains.enrolment.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ConsentState;
import com.zimasahealth.zcare.common.consent.ConsentStatus;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.dto.MemberCareContext;
import com.zimasahealth.zcare.domains.reference.dto.MemberView;
import com.zimasahealth.zcare.domains.reference.services.MemberReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /members/{id}/care-context} (04B section 11; 04C S-06). The consent gate as the
 * caller experiences it at query time:
 * <ul>
 *   <li>consent in force: success, with the health context from every domain's contributor;</li>
 *   <li>no or pending consent: ZCARE_CONSENT_REQUIRED, header only;</li>
 *   <li>withdrawn: ZCARE_CONSENT_REVOKED, header only, no next actions.</li>
 * </ul>
 * Each read of a member's context is audited.
 */
@Service
@Transactional
public class MemberCareContextService {

    private final MemberReferenceService members;
    private final EnrolmentQueryService enrolments;
    private final ConsentGate consentGate;
    private final List<CareContextContributor> contributors;
    private final AuditWriter audit;

    public MemberCareContextService(MemberReferenceService members, EnrolmentQueryService enrolments,
                                    ConsentGate consentGate, List<CareContextContributor> contributors,
                                    AuditWriter audit) {
        this.members = members;
        this.enrolments = enrolments;
        this.consentGate = consentGate;
        this.contributors = contributors;
        this.audit = audit;
    }

    public ServiceResult<MemberCareContext> read(long memberId, Long enrolmentId) {
        MemberView member = members.require(memberId, "memberId");
        EnrolmentView enrolment = enrolmentId != null
                ? enrolments.require(enrolmentId, "enrolmentId")
                : enrolments.currentForMember(memberId).orElse(null);
        if (enrolment != null && !enrolment.memberId().equals(memberId)) {
            throw BusinessException.invalid("enrolmentId", "Enrolment " + enrolmentId + " is not this member's");
        }
        audit.record(AuditEntry.of(EnrolmentOperations.VIEW_MEMBER_CONTEXT, "member_reference", memberId)
                .member(memberId).programmeVersion(enrolment == null ? null : enrolment.programmeVersionId()));

        if (enrolment == null) {
            MemberCareContext header = new MemberCareContext(member, null,
                    new MemberCareContext.Consent(ConsentState.NONE.code(), null, List.of()), null);
            return ServiceResult.of(header).next("proceed").session("memberId", memberId);
        }
        ConsentStatus consent = consentGate.status(enrolment.id());
        MemberCareContext.Consent consentSummary = new MemberCareContext.Consent(consent.state().code(),
                consent.wordingVersion(), consent.scope().stream().map(ContentClass::code).sorted().toList());
        ServiceResult<MemberCareContext> result;
        if (consent.state() == ConsentState.REVOKED) {
            result = ServiceResult.of(new MemberCareContext(member, enrolment, consentSummary, null))
                    .warning(ZCareExceptionCode.ZCARE_CONSENT_REVOKED, "The member has withdrawn consent",
                            Map.of("enrolmentId", enrolment.id(), "consentState", consent.state().code()));
        } else if (!consent.covers(ContentClass.HEALTH_CONTENT)) {
            result = ServiceResult.of(new MemberCareContext(member, enrolment, consentSummary, null))
                    .warning(ZCareExceptionCode.ZCARE_CONSENT_REQUIRED,
                            "No valid consent in force; the member's health context is withheld",
                            Map.of("enrolmentId", enrolment.id(), "consentState", consent.state().code()));
        } else {
            Map<String, Object> health = new LinkedHashMap<>();
            for (CareContextContributor contributor : contributors) {
                health.put(contributor.section(), contributor.contribute(enrolment.id()));
            }
            result = ServiceResult.of(new MemberCareContext(member, enrolment, consentSummary, health))
                    .next("invited".equals(enrolment.status()) ? "activate_enrolment" : "proceed");
        }
        return result.session("memberId", memberId).session("enrolmentId", enrolment.id());
    }
}
