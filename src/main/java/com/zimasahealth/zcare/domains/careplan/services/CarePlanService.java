package com.zimasahealth.zcare.domains.careplan.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.consent.ConsentGate;
import com.zimasahealth.zcare.common.consent.ContentClass;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.context.ZcareRole;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.outbox.DataClassification;
import com.zimasahealth.zcare.common.outbox.DomainEventType;
import com.zimasahealth.zcare.common.outbox.OutboxWriter;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanActivatedEvent;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanDecidedEvent;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanSubmittedEvent;
import com.zimasahealth.zcare.domains.careplan.dto.CarePlanView;
import com.zimasahealth.zcare.domains.careplan.dto.CreateCarePlanRequest;
import com.zimasahealth.zcare.domains.careplan.entities.CarePlan;
import com.zimasahealth.zcare.domains.careplan.entities.Goal;
import com.zimasahealth.zcare.domains.careplan.entities.Intervention;
import com.zimasahealth.zcare.domains.careplan.entities.PlanReview;
import com.zimasahealth.zcare.domains.careplan.mappers.CarePlanMapper;
import com.zimasahealth.zcare.domains.careplan.repositories.CarePlanRepository;
import com.zimasahealth.zcare.domains.careplan.repositories.GoalRepository;
import com.zimasahealth.zcare.domains.careplan.repositories.InterventionRepository;
import com.zimasahealth.zcare.domains.careplan.repositories.PlanReviewRepository;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import com.zimasahealth.zcare.domains.programme.dto.GoalTypeView;
import com.zimasahealth.zcare.domains.programme.services.ProgrammeQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Care planning (04A section 7.5; UC-ZC-002): a care manager drafts and submits; only a
 * clinician approves, and never the plan's own author (OD-20). Approval activates the plan and
 * turns its interventions into tasks. A revision is a new version that replaces the active plan
 * only once it, too, is approved; each enrolment has one active plan
 * ({@code uq_zc_care_plan_one_active}).
 */
@Service
@Transactional
public class CarePlanService {

    private static final List<String> LIVE = List.of("draft", "pending_approval", "active");
    private static final List<String> IN_PREPARATION = List.of("draft", "pending_approval");

    private final CarePlanRepository plans;
    private final GoalRepository goals;
    private final InterventionRepository interventions;
    private final PlanReviewRepository reviews;
    private final CarePlanMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ProgrammeQueryService programmes;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final ApplicationEventPublisher events;

    public CarePlanService(CarePlanRepository plans, GoalRepository goals, InterventionRepository interventions,
                           PlanReviewRepository reviews, CarePlanMapper mapper, EnrolmentQueryService enrolments,
                           ProgrammeQueryService programmes, ConsentGate consentGate, AuditWriter audit,
                           OutboxWriter outbox, ApplicationEventPublisher events) {
        this.plans = plans;
        this.goals = goals;
        this.interventions = interventions;
        this.reviews = reviews;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.programmes = programmes;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CarePlanView get(long carePlanId) {
        CarePlan plan = requirePlan(carePlanId);
        consentGate.require(plan.getEnrolmentId(), ContentClass.HEALTH_CONTENT);
        return view(plan);
    }

    public ServiceResult<CarePlanView> create(CreateCarePlanRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        plans.findFirstByEnrolmentIdAndStatusIn(enrolment.id(), LIVE).ifPresent(existing -> {
            throw BusinessException.invalid("enrolmentId", "This enrolment already has a " + existing.getStatus()
                            + " care plan; revise it instead of creating another")
                    .with("existingCarePlanId", existing.getId());
        });
        List<Long> goalTypeIds = new ArrayList<>();
        for (int i = 0; i < request.goals().size(); i++) {
            CreateCarePlanRequest.GoalInput goal = request.goals().get(i);
            GoalTypeView type = programmes.goalType(enrolment.programmeVersionId(), goal.goalTypeCode())
                    .orElseThrow(invalid("goals[" + i + "].goalTypeCode", "Goal type '" + goal.goalTypeCode()
                            + "' is not configured for this programme version"));
            boolean measurable = (goal.targetValue() != null && goal.targetUnit() != null && !goal.targetUnit().isBlank())
                    || (goal.targetCriteria() != null && !goal.targetCriteria().isBlank());
            if (!measurable) {
                throw BusinessException.invalid("goals[" + i + "].targetValue",
                        "A goal needs a measurable target: a value with its unit, or target criteria");
            }
            goalTypeIds.add(type.id());
        }
        List<CreateCarePlanRequest.InterventionInput> requestedInterventions =
                request.interventions() == null ? List.of() : request.interventions();
        for (int i = 0; i < requestedInterventions.size(); i++) {
            CreateCarePlanRequest.InterventionInput intervention = requestedInterventions.get(i);
            if (ZcareRole.fromCode(intervention.ownerRole()).isEmpty()) {
                throw BusinessException.invalid("interventions[" + i + "].ownerRole",
                        "Unknown role '" + intervention.ownerRole() + "'");
            }
            if (intervention.goalIndex() != null && intervention.goalIndex() >= request.goals().size()) {
                throw BusinessException.invalid("interventions[" + i + "].goalIndex", "No goal at that index");
            }
        }

        CarePlan plan = plans.save(new CarePlan(enrolment.id(), enrolment.programmeVersionId(),
                plans.maxVersionNumber(enrolment.id()) + 1, null, request.summary()));
        List<Long> goalIds = new ArrayList<>();
        for (int i = 0; i < request.goals().size(); i++) {
            CreateCarePlanRequest.GoalInput goal = request.goals().get(i);
            goalIds.add(goals.save(new Goal(plan.getId(), plan.getProgrammeVersionId(), goalTypeIds.get(i),
                    goal.description(), goal.targetValue(), goal.targetUnit(), goal.targetCriteria(),
                    goal.targetDate())).getId());
        }
        for (CreateCarePlanRequest.InterventionInput intervention : requestedInterventions) {
            interventions.save(new Intervention(plan.getId(),
                    intervention.goalIndex() == null ? null : goalIds.get(intervention.goalIndex()),
                    ZcareRole.fromCode(intervention.ownerRole()).orElseThrow().code(), intervention.description(),
                    intervention.frequency()));
        }
        audit.record(AuditEntry.of(CarePlanOperations.CREATE_CARE_PLAN, "care_plan", plan.getId())
                .member(enrolment.memberId()).programmeVersion(plan.getProgrammeVersionId())
                .newState(Map.of("status", "draft", "versionNumber", plan.getVersionNumber(),
                        "goals", goalIds.size())));
        return ServiceResult.of(view(plan)).next("submit_for_approval")
                .session("carePlanId", plan.getId()).session("enrolmentId", enrolment.id());
    }

    public ServiceResult<CarePlanView> submit(long carePlanId) {
        CarePlan plan = requirePlan(carePlanId);
        if (!"draft".equals(plan.getStatus())) {
            throw BusinessException.invalid("carePlanId", "Only a draft plan can be submitted; this one is "
                    + plan.getStatus());
        }
        if (goals.countByCarePlanId(carePlanId) == 0) {
            throw BusinessException.invalid("goals", "A plan without goals cannot be submitted");
        }
        EnrolmentView enrolment = enrolments.require(plan.getEnrolmentId(), "carePlanId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        plan.submit(Times.now());
        events.publishEvent(new CarePlanSubmittedEvent(carePlanId, enrolment.id(), enrolment.memberId(),
                plan.getVersionNumber()));
        audit.record(AuditEntry.of(CarePlanOperations.SUBMIT_FOR_APPROVAL, "care_plan", carePlanId)
                .member(enrolment.memberId()).programmeVersion(plan.getProgrammeVersionId())
                .previousState(Map.of("status", "draft")).newState(Map.of("status", "pending_approval")));
        // The plan now holds until a clinician decides; nothing moves it on by itself (UC-ZC-002 E6).
        return ServiceResult.of(view(plan)).next("view_member_context").session("carePlanId", carePlanId);
    }

    /** Clinician approval: the plan becomes active and its interventions become tasks. */
    public ServiceResult<CarePlanView> activate(long carePlanId) {
        CarePlan plan = requirePendingPlan(carePlanId);
        String clinician = CurrentActor.require().id();
        if (clinician.equals(plan.getCreatedBy())) {
            throw new AccessDeniedException("The author of a care plan cannot approve it");
        }
        EnrolmentView enrolment = enrolments.require(plan.getEnrolmentId(), "carePlanId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);

        Instant now = Times.now();
        Optional<CarePlan> superseded = plans.findFirstByEnrolmentIdAndStatus(enrolment.id(), "active");
        superseded.ifPresent(previous -> {
            previous.markRevised();
            plans.flush(); // one active plan per enrolment: retire the old before activating the new
        });
        plan.approveAndActivate(clinician, now);
        reviews.save(new PlanReview(carePlanId, "approved", clinician, now, null));
        List<Intervention> planned = interventions.findByCarePlanIdOrderById(carePlanId);
        planned.forEach(Intervention::activate);

        events.publishEvent(new CarePlanDecidedEvent(carePlanId, "approved"));
        events.publishEvent(new CarePlanActivatedEvent(carePlanId, enrolment.id(), enrolment.memberId(),
                planned.stream().map(i -> new CarePlanActivatedEvent.ActivatedIntervention(i.getId(),
                        i.getOwnerRole(), i.getDescription())).toList()));
        outbox.emit(DomainEventType.CarePlanActivated, "care_plan", carePlanId,
                Map.of("carePlanId", carePlanId, "enrolmentId", enrolment.id(),
                        "versionNumber", plan.getVersionNumber(), "approvedBy", clinician),
                DataClassification.PHI);
        audit.record(AuditEntry.of(CarePlanOperations.APPROVE_CARE_PLAN, "care_plan", carePlanId)
                .member(enrolment.memberId()).programmeVersion(plan.getProgrammeVersionId())
                .previousState(Map.of("status", "pending_approval"))
                .newState(Map.of("status", "active", "approvedBy", clinician,
                        "supersededPlanId", superseded.map(CarePlan::getId).orElse(0L))));
        return ServiceResult.of(view(plan)).next("view_member_context")
                .session("carePlanId", carePlanId).session("enrolmentId", enrolment.id());
    }

    public ServiceResult<CarePlanView> requestChanges(long carePlanId, String notes) {
        CarePlan plan = requirePendingPlan(carePlanId);
        plan.returnToDraft();
        return decide(plan, "changes_requested", notes, CarePlanOperations.REQUEST_PLAN_CHANGES);
    }

    public ServiceResult<CarePlanView> reject(long carePlanId, String notes) {
        CarePlan plan = requirePendingPlan(carePlanId);
        plan.reject(notes);
        return decide(plan, "rejected", notes, CarePlanOperations.REJECT_CARE_PLAN);
    }

    /** A new draft version that starts from the approved care; the active plan stays until it is approved. */
    public ServiceResult<CarePlanView> revise(long carePlanId) {
        CarePlan active = requirePlan(carePlanId);
        if (!"active".equals(active.getStatus())) {
            throw BusinessException.invalid("carePlanId", "Only an active plan can be revised; this one is "
                    + active.getStatus());
        }
        plans.findFirstByEnrolmentIdAndStatusIn(active.getEnrolmentId(), IN_PREPARATION).ifPresent(open -> {
            throw BusinessException.invalid("carePlanId", "A revision is already " + open.getStatus())
                    .with("existingCarePlanId", open.getId());
        });
        EnrolmentView enrolment = enrolments.requireActive(active.getEnrolmentId(), "carePlanId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        CarePlan revision = plans.save(new CarePlan(active.getEnrolmentId(), active.getProgrammeVersionId(),
                plans.maxVersionNumber(active.getEnrolmentId()) + 1, active.getId(), active.getSummary()));
        Map<Long, Long> goalIds = new HashMap<>();
        for (Goal goal : goals.findByCarePlanIdOrderById(active.getId())) {
            goalIds.put(goal.getId(), goals.save(goal.copyTo(revision.getId())).getId());
        }
        for (Intervention intervention : interventions.findByCarePlanIdOrderById(active.getId())) {
            interventions.save(new Intervention(revision.getId(),
                    intervention.getGoalId() == null ? null : goalIds.get(intervention.getGoalId()),
                    intervention.getOwnerRole(), intervention.getDescription(), intervention.getFrequency()));
        }
        outbox.emit(DomainEventType.CarePlanRevised, "care_plan", revision.getId(),
                Map.of("carePlanId", revision.getId(), "supersedes", active.getId(),
                        "versionNumber", revision.getVersionNumber()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(CarePlanOperations.REVISE_CARE_PLAN, "care_plan", revision.getId())
                .member(enrolment.memberId()).programmeVersion(revision.getProgrammeVersionId())
                .newState(Map.of("status", "draft", "versionNumber", revision.getVersionNumber(),
                        "supersedes", active.getId())));
        return ServiceResult.of(view(revision)).next("submit_for_approval")
                .session("carePlanId", revision.getId()).session("supersedesCarePlanId", active.getId());
    }

    private ServiceResult<CarePlanView> decide(CarePlan plan, String outcome, String notes, String operation) {
        EnrolmentView enrolment = enrolments.require(plan.getEnrolmentId(), "carePlanId");
        reviews.save(new PlanReview(plan.getId(), outcome, CurrentActor.require().id(), Times.now(), notes));
        events.publishEvent(new CarePlanDecidedEvent(plan.getId(), outcome));
        audit.record(AuditEntry.of(operation, "care_plan", plan.getId())
                .member(enrolment.memberId()).programmeVersion(plan.getProgrammeVersionId())
                .previousState(Map.of("status", "pending_approval")).newState(Map.of("status", plan.getStatus()))
                .reason(notes));
        return ServiceResult.of(view(plan)).next("proceed").session("carePlanId", plan.getId());
    }

    private CarePlan requirePlan(long carePlanId) {
        return plans.findById(carePlanId).orElseThrow(() -> BusinessException.unknown("carePlanId", "care plan"));
    }

    private CarePlan requirePendingPlan(long carePlanId) {
        CarePlan plan = requirePlan(carePlanId);
        if (!"pending_approval".equals(plan.getStatus())) {
            throw BusinessException.invalid("carePlanId", "Only a plan pending approval can be decided; this one is "
                    + plan.getStatus());
        }
        return plan;
    }

    private CarePlanView view(CarePlan plan) {
        return mapper.toView(plan,
                goals.findByCarePlanIdOrderById(plan.getId()).stream().map(mapper::toView).toList(),
                interventions.findByCarePlanIdOrderById(plan.getId()).stream().map(mapper::toView).toList(),
                reviews.findByCarePlanIdOrderByReviewedAtAsc(plan.getId()).stream().map(mapper::toView).toList());
    }

    private static Supplier<BusinessException> invalid(String field, String message) {
        return () -> BusinessException.invalid(field, message);
    }
}
