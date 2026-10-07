package com.zimasahealth.zcare.domains.cohort.services;

import java.time.LocalDate;
import java.util.Map;

import com.zimasahealth.zcare.common.api.PageParams;
import com.zimasahealth.zcare.common.api.PagedResponse;
import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.domains.access.services.ConfigKeys;
import com.zimasahealth.zcare.domains.access.services.ConfigService;
import com.zimasahealth.zcare.domains.cohort.dto.AddCohortMemberRequest;
import com.zimasahealth.zcare.domains.cohort.dto.CohortMembershipView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortRunView;
import com.zimasahealth.zcare.domains.cohort.dto.CohortView;
import com.zimasahealth.zcare.domains.cohort.dto.CreateCohortRequest;
import com.zimasahealth.zcare.domains.cohort.dto.InvitableMembership;
import com.zimasahealth.zcare.domains.cohort.entities.Cohort;
import com.zimasahealth.zcare.domains.cohort.entities.CohortMembership;
import com.zimasahealth.zcare.domains.cohort.entities.CohortRun;
import com.zimasahealth.zcare.domains.cohort.mappers.CohortMapper;
import com.zimasahealth.zcare.domains.cohort.repositories.CohortMembershipRepository;
import com.zimasahealth.zcare.domains.cohort.repositories.CohortRepository;
import com.zimasahealth.zcare.domains.cohort.repositories.CohortRunRepository;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import com.zimasahealth.zcare.domains.reference.dto.MemberView;
import com.zimasahealth.zcare.domains.reference.services.MemberReferenceService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Population and cohort (04A section 7.2; UC-ZC-011). Members join a cohort by manual inclusion
 * or by identification through the payer; an implausibly large identification is held for
 * review rather than applied. Releasing the cohort is the human gate before invitations.
 */
@Service
@Transactional
public class CohortService {

    static final int DEFAULT_MAX_PLAUSIBLE_VOLUME = 5000;
    private static final String INCLUDED = "included";

    private final CohortRepository cohorts;
    private final CohortRunRepository runs;
    private final CohortMembershipRepository memberships;
    private final CohortMapper mapper;
    private final ProgrammeQueryService programmes;
    private final MemberReferenceService members;
    private final ConfigService config;
    private final PayerPort payer;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public CohortService(CohortRepository cohorts, CohortRunRepository runs, CohortMembershipRepository memberships,
                         CohortMapper mapper, ProgrammeQueryService programmes, MemberReferenceService members,
                         ConfigService config, PayerPort payer, AuditWriter audit, OutboxWriter outbox) {
        this.cohorts = cohorts;
        this.runs = runs;
        this.memberships = memberships;
        this.mapper = mapper;
        this.programmes = programmes;
        this.members = members;
        this.config = config;
        this.payer = payer;
        this.audit = audit;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public PagedResponse<CohortView> list(PageParams params) {
        return PagedResponse.of(cohorts.findAll(params.toPageable(Sort.by(Sort.Direction.DESC, "createdAt"))), params,
                cohort -> mapper.toView(cohort, memberships.countByCohortIdAndStatus(cohort.getId(), INCLUDED)));
    }

    @Transactional(readOnly = true)
    public PagedResponse<CohortMembershipView> members(long cohortId, PageParams params) {
        requireCohort(cohortId);
        return PagedResponse.of(memberships.findByCohortId(cohortId, params.toPageable(Sort.by("id"))), params,
                membership -> mapper.toView(membership, false));
    }

    public ServiceResult<CohortView> create(CreateCohortRequest request) {
        programmes.requirePublishedVersion(request.programmeVersionId(), "programmeVersionId");
        if (cohorts.existsByProgrammeVersionIdAndCode(request.programmeVersionId(), request.code())) {
            throw BusinessException.invalid("code", "Cohort code '" + request.code()
                    + "' is already used for this programme version");
        }
        Cohort cohort = cohorts.save(new Cohort(request.programmeVersionId(), request.code(), request.name(),
                request.description()));
        audit.record(AuditEntry.of(CohortOperations.CREATE_COHORT, "cohort", cohort.getId())
                .programmeVersion(cohort.getProgrammeVersionId())
                .newState(Map.of("code", cohort.getCode(), "status", cohort.getStatus())));
        return ServiceResult.of(mapper.toView(cohort, 0L)).next("add_member", "identify_cohort")
                .session("cohortId", cohort.getId()).session("programmeVersionId", cohort.getProgrammeVersionId());
    }

    /** Manual inclusion. A member already in the cohort is absorbed, not duplicated (UC-ZC-011 E4). */
    public ServiceResult<CohortMembershipView> addMember(long cohortId, AddCohortMemberRequest request) {
        Cohort cohort = requireOpenCohort(cohortId);
        MemberView member;
        if (request.memberId() != null) {
            member = members.require(request.memberId(), "memberId");
        } else if (request.member() != null) {
            member = members.resolveOrCreate(request.member());
        } else {
            throw BusinessException.required("memberId", "Give memberId, or member to identify them at source");
        }
        var existing = memberships.findByCohortIdAndMemberId(cohortId, member.id());
        if (existing.isPresent()) {
            return ServiceResult.of(mapper.toView(existing.get(), true)).next("proceed")
                    .session("cohortId", cohortId).session("memberId", member.id())
                    .session("cohortMembershipId", existing.get().getId());
        }
        CohortMembership membership = memberships.save(new CohortMembership(cohortId, member.id(), null, "manual",
                null, request.inclusionReason(), member.sourceDate()));
        audit.record(AuditEntry.of(CohortOperations.ADD_COHORT_MEMBER, "cohort_membership", membership.getId())
                .member(member.id()).programmeVersion(cohort.getProgrammeVersionId())
                .reason(request.inclusionReason()));
        return ServiceResult.of(mapper.toView(membership, false))
                .next("draft".equals(cohort.getStatus()) ? new String[] {"add_member", "release_cohort"}
                        : new String[] {"invite_member"})
                .session("cohortId", cohortId).session("memberId", member.id())
                .session("cohortMembershipId", membership.getId());
    }

    /**
     * Identification through the payer. Every outcome is kept as a run: completed, failed because
     * the payer is unavailable, or held for review because the volume is implausible.
     */
    public ServiceResult<CohortRunView> identify(long cohortId) {
        Cohort cohort = requireOpenCohort(cohortId);
        CohortRun run = runs.save(new CohortRun(cohortId, "programme_version:" + cohort.getProgrammeVersionId()));
        PayerPort.Identification result;
        try {
            result = payer.identify(cohort.getProgrammeVersionId(), run.getRuleReference());
        } catch (PayerPort.PayerUnavailableException e) {
            run.fail(e.getMessage());
            audit.record(AuditEntry.of(CohortOperations.IDENTIFY_COHORT, "cohort_run", run.getId())
                    .programmeVersion(cohort.getProgrammeVersionId())
                    .newState(Map.of("status", run.getStatus())));
            return ServiceResult.of(mapper.toView(run, 0))
                    .warning(ZCareExceptionCode.ZCARE_EXTERNAL_DEPENDENCY_UNAVAILABLE,
                            "The payer could not be reached; nothing was identified", Map.of("cohortRunId", run.getId()))
                    .session("cohortId", cohortId).session("cohortRunId", run.getId());
        }

        int maxVolume = config.intValue(ConfigKeys.COHORT, ConfigKeys.MAX_PLAUSIBLE_VOLUME, DEFAULT_MAX_PLAUSIBLE_VOLUME);
        if (result.members().size() > maxVolume) {
            run.hold(result.members().size(), "Identified " + result.members().size()
                    + " members, above the plausible maximum of " + maxVolume);
            audit.record(AuditEntry.of(CohortOperations.IDENTIFY_COHORT, "cohort_run", run.getId())
                    .programmeVersion(cohort.getProgrammeVersionId())
                    .newState(Map.of("status", run.getStatus(), "resultCount", run.getResultCount())));
            return ServiceResult.of(mapper.toView(run, 0))
                    .warning(ZCareExceptionCode.ZCARE_COHORT_REVIEW_REQUIRED, run.getHoldReason(),
                            Map.of("cohortRunId", run.getId(), "resultCount", run.getResultCount()))
                    .session("cohortId", cohortId).session("cohortRunId", run.getId());
        }

        LocalDate sourceDate = result.sourceDataDate() == null ? LocalDate.now() : result.sourceDataDate();
        int added = 0;
        for (PayerPort.IdentifiedMember identified : result.members()) {
            MemberView member = members.resolveOrCreate(identified.identity());
            if (memberships.findByCohortIdAndMemberId(cohortId, member.id()).isPresent()) {
                continue;
            }
            CohortMembership membership = memberships.save(new CohortMembership(cohortId, member.id(), run.getId(),
                    identified.inclusionMethod(), identified.inclusionSourceDetail(), identified.inclusionReason(),
                    sourceDate));
            outbox.emit(DomainEventType.CohortMemberIdentified, "cohort", cohortId,
                    Map.of("cohortId", cohortId, "cohortMembershipId", membership.getId(), "memberId", member.id(),
                            "inclusionMethod", membership.getInclusionMethod()),
                    DataClassification.PHI);
            added++;
        }
        run.complete(result.members().size(), sourceDate);
        audit.record(AuditEntry.of(CohortOperations.IDENTIFY_COHORT, "cohort_run", run.getId())
                .programmeVersion(cohort.getProgrammeVersionId())
                .newState(Map.of("status", run.getStatus(), "resultCount", run.getResultCount(), "added", added)));
        return ServiceResult.of(mapper.toView(run, added)).next("release_cohort")
                .session("cohortId", cohortId).session("cohortRunId", run.getId());
    }

    /** The review gate: a draft cohort with at least one included member becomes active. */
    public ServiceResult<CohortView> release(long cohortId) {
        Cohort cohort = requireCohort(cohortId);
        long included = memberships.countByCohortIdAndStatus(cohortId, INCLUDED);
        if ("active".equals(cohort.getStatus())) {
            return ServiceResult.of(mapper.toView(cohort, included)).next("invite_member").session("cohortId", cohortId);
        }
        if (!"draft".equals(cohort.getStatus())) {
            throw BusinessException.invalid("cohortId", "Cohort " + cohortId + " is " + cohort.getStatus());
        }
        if (included == 0) {
            throw BusinessException.invalid("cohortId", "An empty cohort cannot be released; add members first");
        }
        cohort.setStatus("active");
        audit.record(AuditEntry.of(CohortOperations.RELEASE_COHORT, "cohort", cohortId)
                .programmeVersion(cohort.getProgrammeVersionId())
                .newState(Map.of("status", "active", "includedMembers", included)));
        return ServiceResult.of(mapper.toView(cohort, included)).next("invite_member").session("cohortId", cohortId);
    }

    /**
     * The membership behind an invitation, which must be included and in a released cohort; the
     * reason it cannot be invited is reported on {@code cohortMembershipId}.
     */
    @Transactional(readOnly = true)
    public InvitableMembership requireInvitable(long membershipId) {
        CohortMembership membership = memberships.findById(membershipId)
                .orElseThrow(() -> BusinessException.unknown("cohortMembershipId", "cohort membership"));
        if (!INCLUDED.equals(membership.getStatus())) {
            throw BusinessException.invalid("cohortMembershipId", "The membership is " + membership.getStatus());
        }
        Cohort cohort = cohorts.findById(membership.getCohortId()).orElseThrow();
        if (!"active".equals(cohort.getStatus())) {
            throw BusinessException.invalid("cohortMembershipId",
                    "The cohort has not been released for enrolment").with("cohortStatus", cohort.getStatus());
        }
        return new InvitableMembership(membership.getId(), cohort.getId(), membership.getMemberId(),
                cohort.getProgrammeVersionId());
    }

    private Cohort requireCohort(long cohortId) {
        return cohorts.findById(cohortId).orElseThrow(() -> BusinessException.unknown("cohortId", "cohort"));
    }

    private Cohort requireOpenCohort(long cohortId) {
        Cohort cohort = requireCohort(cohortId);
        if ("closed".equals(cohort.getStatus())) {
            throw BusinessException.invalid("cohortId", "Cohort " + cohortId + " is closed");
        }
        return cohort;
    }
}
