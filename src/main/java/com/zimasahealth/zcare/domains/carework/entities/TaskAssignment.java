package com.zimasahealth.zcare.domains.carework.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.AppendOnlyTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** {@code zc_task_assignment_history}: append-only record of every (re)assignment. */
@Entity
@Table(name = "zc_task_assignment_history")
@Immutable
public class TaskAssignment extends AppendOnlyTenantEntity {

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "from_actor")
    private String fromActor;

    @Column(name = "to_actor")
    private String toActor;

    @Column(name = "from_role")
    private String fromRole;

    @Column(name = "to_role")
    private String toRole;

    @Column(name = "reason")
    private String reason;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected TaskAssignment() {
    }

    /** The first assignment of a new task. */
    public static TaskAssignment initial(Task task, Instant at) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.taskId = task.getId();
        assignment.toActor = task.getAssignedToActor();
        assignment.toRole = task.getAssignedToRole();
        assignment.reason = "created";
        assignment.assignedAt = at;
        return assignment;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getFromActor() {
        return fromActor;
    }

    public String getToActor() {
        return toActor;
    }

    public String getFromRole() {
        return fromRole;
    }

    public String getToRole() {
        return toRole;
    }

    public String getReason() {
        return reason;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
