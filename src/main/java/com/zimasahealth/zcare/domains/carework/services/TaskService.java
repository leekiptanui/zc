package com.zimasahealth.zcare.domains.carework.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import com.zimasahealth.zcare.domains.carework.dto.CompleteTaskRequest;
import com.zimasahealth.zcare.domains.carework.dto.CreateTaskRequest;
import com.zimasahealth.zcare.domains.carework.dto.SystemTask;
import com.zimasahealth.zcare.domains.carework.dto.TaskView;
import com.zimasahealth.zcare.domains.carework.entities.Task;
import com.zimasahealth.zcare.domains.carework.entities.TaskAssignment;
import com.zimasahealth.zcare.domains.carework.mappers.TaskMapper;
import com.zimasahealth.zcare.domains.carework.repositories.TaskAssignmentRepository;
import com.zimasahealth.zcare.domains.carework.repositories.TaskDependencyRepository;
import com.zimasahealth.zcare.domains.carework.repositories.TaskRepository;
import com.zimasahealth.zcare.domains.carework.specifications.TaskSpecifications;
import com.zimasahealth.zcare.domains.enrolment.dto.EnrolmentView;
import com.zimasahealth.zcare.domains.enrolment.services.EnrolmentQueryService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Care work (04A section 7.6; UC-ZC-003). A restricted task completed by the wrong role is a
 * visible escalation (ZCARE_TASK_RESTRICTED), not a silent 403, so the care manager sees why; an
 * open prerequisite blocks completion and is named.
 */
@Service
@Transactional
public class TaskService {

    private final TaskRepository tasks;
    private final TaskAssignmentRepository assignments;
    private final TaskDependencyRepository dependencies;
    private final TaskMapper mapper;
    private final EnrolmentQueryService enrolments;
    private final ConsentGate consentGate;
    private final AuditWriter audit;
    private final OutboxWriter outbox;

    public TaskService(TaskRepository tasks, TaskAssignmentRepository assignments,
                       TaskDependencyRepository dependencies, TaskMapper mapper, EnrolmentQueryService enrolments,
                       ConsentGate consentGate, AuditWriter audit, OutboxWriter outbox) {
        this.tasks = tasks;
        this.assignments = assignments;
        this.dependencies = dependencies;
        this.mapper = mapper;
        this.enrolments = enrolments;
        this.consentGate = consentGate;
        this.audit = audit;
        this.outbox = outbox;
    }

    /** A role's prioritised queue: most urgent first, then soonest due. */
    @Transactional(readOnly = true)
    public PagedResponse<TaskView> queue(String role, PageParams params) {
        ZcareRole queueRole = ZcareRole.fromCode(role)
                .orElseThrow(() -> BusinessException.invalid("role", "Unknown role '" + role + "'"));
        Actor actor = CurrentActor.require();
        if (!actor.hasRole(queueRole) && !actor.hasRole(ZcareRole.PLATFORM_ADMIN)) {
            throw new AccessDeniedException("A caller reads only the queue of a role they hold");
        }
        Specification<Task> filter = Specification.where(TaskSpecifications.isOpen())
                .and(TaskSpecifications.inQueueOf(queueRole.code(), actor.id()));
        Sort order = Sort.by(Sort.Order.asc("priority"), Sort.Order.asc("dueAt").nullsLast(), Sort.Order.asc("id"));
        return PagedResponse.of(tasks.findAll(filter, params.toPageable(order)), params, mapper::toView);
    }

    @Transactional(readOnly = true)
    public List<TaskView> openForEnrolment(long enrolmentId) {
        return tasks.findByEnrolmentIdAndStatusIn(enrolmentId, TaskSpecifications.OPEN).stream()
                .map(mapper::toView).toList();
    }

    public ServiceResult<TaskView> create(CreateTaskRequest request) {
        EnrolmentView enrolment = enrolments.requireActive(request.enrolmentId(), "enrolmentId");
        consentGate.require(enrolment.id(), ContentClass.HEALTH_CONTENT);
        String role = null;
        if (request.assignedToRole() != null) {
            role = ZcareRole.fromCode(request.assignedToRole()).map(ZcareRole::code)
                    .orElseThrow(() -> BusinessException.invalid("assignedToRole",
                            "Unknown role '" + request.assignedToRole() + "'"));
        }
        String person = request.assignedToActor() == null || request.assignedToActor().isBlank()
                ? null : request.assignedToActor().trim();
        if (person == null && role == null) {
            throw BusinessException.required("assignedToActor", "Assign the task to a person, a role, or both");
        }
        Task task = tasks.save(new Task(enrolment.id(), enrolment.memberId(), request.taskType(), request.title(),
                request.description(), (short) (request.priority() == null ? 3 : request.priority()), person, role,
                request.dueAt(), "manual", null));
        recordCreated(task, TaskOperations.ASSIGN_TASK);
        return ServiceResult.of(mapper.toView(task)).next("proceed")
                .session("taskId", task.getId()).session("enrolmentId", enrolment.id());
    }

    public ServiceResult<TaskView> complete(long taskId, CompleteTaskRequest request) {
        Task task = tasks.findById(taskId).orElseThrow(() -> BusinessException.unknown("taskId", "task"));
        if (!task.isOpen() || "blocked".equals(task.getStatus())) {
            throw BusinessException.invalid("taskId", "The task is " + task.getStatus() + " and cannot be completed");
        }
        if (TaskTypes.CLOSED_BY_DECISION.contains(task.getTaskType())) {
            throw BusinessException.invalid("taskId",
                    "This task closes when the clinician decides the care plan; it cannot be completed directly");
        }
        Actor actor = CurrentActor.require();
        TaskTypes.restrictedTo(task.getTaskType()).filter(role -> !actor.hasRole(role)).ifPresent(role -> {
            throw BusinessException.of(ZCareExceptionCode.ZCARE_TASK_RESTRICTED,
                            "Only a " + role.code() + " may complete a " + task.getTaskType() + " task;"
                                    + " it has been left for the role owner")
                    .with("requiredRole", role.code()).with("taskId", taskId);
        });
        if (!TaskTypes.CONSENT_EXEMPT.contains(task.getTaskType())) {
            consentGate.require(task.getEnrolmentId(), ContentClass.HEALTH_CONTENT);
        }
        List<Long> blockers = dependencies.findOpenPrerequisites(taskId);
        if (!blockers.isEmpty()) {
            throw BusinessException.invalid("taskId", "The task is blocked by open prerequisite task(s) " + blockers)
                    .with("blockedBy", blockers);
        }
        task.complete(actor.id(), request == null ? null : request.completionNote(), Times.now());
        outbox.emit(DomainEventType.TaskCompleted, "task", taskId,
                Map.of("taskId", taskId, "enrolmentId", task.getEnrolmentId(), "taskType", task.getTaskType()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(TaskOperations.COMPLETE_TASK, "task", taskId)
                .member(task.getMemberId()).newState(Map.of("status", "completed")));
        return ServiceResult.of(mapper.toView(task)).next("view_member_context")
                .session("taskId", taskId).session("enrolmentId", task.getEnrolmentId());
    }

    /** Raises a task on another domain's behalf, audited under the request that caused it. */
    public TaskView raise(SystemTask request) {
        Task task = tasks.save(new Task(request.enrolmentId(), request.memberId(), request.taskType(), request.title(),
                null, request.priority(), null, request.assignedToRole(), request.dueAt(), request.originType(),
                request.originId()));
        recordCreated(task, RequestOperation.currentOr(TaskOperations.RAISE_TASK));
        return mapper.toView(task);
    }

    /** Raises a task for a named person, falling back to a role when no person is responsible. */
    public TaskView raiseFor(SystemTask request, String person) {
        Task task = tasks.save(new Task(request.enrolmentId(), request.memberId(), request.taskType(), request.title(),
                null, request.priority(), person, person == null ? request.assignedToRole() : null, request.dueAt(),
                request.originType(), request.originId()));
        recordCreated(task, RequestOperation.currentOr(TaskOperations.RAISE_TASK));
        return mapper.toView(task);
    }

    /** Cancels every open task of an enrolment, recording why. */
    public int cancelOpen(long enrolmentId, String reason) {
        Instant now = Times.now();
        List<Task> open = tasks.findByEnrolmentIdAndStatusIn(enrolmentId, TaskSpecifications.OPEN);
        for (Task task : open) {
            task.cancel(reason, now);
            audit.record(AuditEntry.of(RequestOperation.currentOr(TaskOperations.CANCEL_TASK), "task", task.getId())
                    .member(task.getMemberId()).newState(Map.of("status", "cancelled")).reason(reason));
        }
        return open.size();
    }

    /** Completes the open tasks a decision closes, such as a plan's approval task. */
    public void completeByOrigin(String originType, long originId, String taskType, String note) {
        Instant now = Times.now();
        String actor = CurrentActor.idOrSystem();
        for (Task task : tasks.findByOriginTypeAndOriginIdAndTaskTypeAndStatusIn(originType, originId, taskType,
                TaskSpecifications.OPEN)) {
            task.complete(actor, note, now);
            outbox.emit(DomainEventType.TaskCompleted, "task", task.getId(),
                    Map.of("taskId", task.getId(), "enrolmentId", task.getEnrolmentId(), "taskType", taskType),
                    DataClassification.PHI);
            audit.record(AuditEntry.of(RequestOperation.currentOr(TaskOperations.COMPLETE_TASK), "task", task.getId())
                    .member(task.getMemberId()).newState(Map.of("status", "completed")).reason(note));
        }
    }

    private void recordCreated(Task task, String operation) {
        assignments.save(TaskAssignment.initial(task, Times.now()));
        outbox.emit(DomainEventType.TaskAssigned, "task", task.getId(),
                Map.of("taskId", task.getId(), "enrolmentId", task.getEnrolmentId(), "taskType", task.getTaskType()),
                DataClassification.PHI);
        audit.record(AuditEntry.of(operation, "task", task.getId()).member(task.getMemberId())
                .newState(Map.of("taskType", task.getTaskType(), "status", task.getStatus())));
    }
}
