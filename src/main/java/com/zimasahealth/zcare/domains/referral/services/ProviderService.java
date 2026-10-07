package com.zimasahealth.zcare.domains.referral.services;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.context.Actor;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.services.OrganisationService;
import com.zimasahealth.zcare.domains.programme.dto.ProgrammeVersionView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import com.zimasahealth.zcare.domains.referral.dto.ProviderActionView;
import com.zimasahealth.zcare.domains.referral.dto.ProviderParticipationView;
import com.zimasahealth.zcare.domains.referral.dto.RecordProviderActionRequest;
import com.zimasahealth.zcare.domains.referral.dto.RegisterParticipationRequest;
import com.zimasahealth.zcare.domains.referral.entities.ProviderAction;
import com.zimasahealth.zcare.domains.referral.entities.ProviderParticipation;
import com.zimasahealth.zcare.domains.referral.mappers.ReferralMapper;
import com.zimasahealth.zcare.domains.referral.repositories.ProviderActionRepository;
import com.zimasahealth.zcare.domains.referral.repositories.ProviderParticipationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider participation (04A section 7.13). Actions are recordable only for an active
 * participant of the programme version, and a provider user records only for their own
 * organisation.
 */
@Service
@Transactional
public class ProviderService {

    private static final List<String> LIVE = List.of("invited", "active", "suspended");
    private static final Set<String> SUBJECT_TYPES = Set.of("referral", "transition", "care_plan", "refill_request",
            "task", "enrolment", "observation");

    private final ProviderParticipationRepository participations;
    private final ProviderActionRepository actions;
    private final ReferralMapper mapper;
    private final ProgrammeQueryService programmes;
    private final OrganisationService organisations;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public ProviderService(ProviderParticipationRepository participations, ProviderActionRepository actions,
                           ReferralMapper mapper, ProgrammeQueryService programmes,
                           OrganisationService organisations, AuditWriter audit, OutboxWriter outbox) {
        this.participations = participations;
        this.actions = actions;
        this.mapper = mapper;
        this.programmes = programmes;
        this.organisations = organisations;
        this.audit = audit;
        this.outbox = outbox;
    }

    public ServiceResult<ProviderParticipationView> register(RegisterParticipationRequest request) {
        ProgrammeVersionView version = programmes.requireVersion(request.programmeVersionId(), "programmeVersionId");
        if ("retired".equals(version.status())) {
            throw BusinessException.invalid("programmeVersionId", "The programme version is retired");
        }
        organisations.requireActive(request.organisationId(), "organisationId");
        participations.findFirstByProgrammeVersionIdAndOrganisationIdAndStatusIn(request.programmeVersionId(),
                request.organisationId(), LIVE).ifPresent(existing -> {
            throw BusinessException.invalid("organisationId", "The organisation already participates in this"
                    + " programme version").with("participationId", existing.getId());
        });
        ProviderParticipation participation = participations.save(ProviderParticipation.active(
                request.programmeVersionId(), request.organisationId(), request.agreementRef(), Times.now()));
        audit.record(AuditEntry.of(ReferralOperations.REGISTER_PROVIDER_PARTICIPATION, "provider_participation",
                        participation.getId())
                .programmeVersion(request.programmeVersionId()).organisation(request.organisationId())
                .newState(Map.of("status", participation.getStatus())));
        return ServiceResult.of(mapper.toView(participation)).next("proceed")
                .session("participationId", participation.getId());
    }

    public ServiceResult<ProviderActionView> recordAction(RecordProviderActionRequest request) {
        if (!SUBJECT_TYPES.contains(request.subjectType())) {
            throw BusinessException.invalid("subjectType", "Unknown subject type '" + request.subjectType() + "'")
                    .with("allowed", SUBJECT_TYPES.stream().sorted().toList());
        }
        ProviderParticipation participation = participations.findById(request.participationId())
                .orElseThrow(() -> BusinessException.unknown("participationId", "provider participation"));
        if (!"active".equals(participation.getStatus())) {
            throw BusinessException.invalid("participationId", "The participation is " + participation.getStatus()
                    + "; only an active participant can record actions");
        }
        Actor actor = CurrentActor.require();
        if (actor.primaryRole() == ZcareRole.PROVIDER_COORDINATOR
                && !participation.getOrganisationId().equals(ReferralService.requireOrganisation(actor))) {
            throw new AccessDeniedException("The participation belongs to another organisation");
        }
        ProviderAction action = actions.save(new ProviderAction(participation.getId(), request.subjectType(),
                request.subjectId(), request.actionCode(), Times.now(), actor.id(), request.evidenceRef(),
                request.notes()));
        outbox.emit(DomainEventType.ProviderActionRecorded, "provider_action", action.getId(),
                Map.of("providerActionId", action.getId(), "participationId", participation.getId(),
                        "subjectType", action.getSubjectType(), "actionCode", action.getActionCode()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(ReferralOperations.RECORD_PROVIDER_ACTION, "provider_action", action.getId())
                .organisation(participation.getOrganisationId())
                .programmeVersion(participation.getProgrammeVersionId())
                .newState(Map.of("subjectType", action.getSubjectType(), "subjectId", action.getSubjectId(),
                        "actionCode", action.getActionCode())));
        return ServiceResult.of(mapper.toView(action)).next("proceed").session("providerActionId", action.getId());
    }
}
