package com.zimasahealth.zcare.domains.carework.entities;

import java.time.Instant;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code zc_task}: a unit of care work, assigned to a person or a role. Completing a task never
 * records a clinical or programme outcome (PRD section 8.7).
 */
@Entity
@Table(name = "zc_task")
@DynamicUpdate
public class Task extends TenantEntity {

    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private Long enrolmentId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "task_type", nullable = false, updatable = false)
    private String taskType;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "priority", nullable = false)
    private Short priority;

    @Column(name = "status", nullable = false)
    private String status = "assigned";

    @Column(name = "assigned_to_actor")
    private String assignedToActor;

    @Column(name = "assigned_to_role")
    private String assignedToRole;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "sla_state", nullable = false)
    private String slaState = "on_track";

    @Column(name = "origin_type", nullable = false, updatable = false)
    private String originType;

    @Column(name = "origin_id", updatable = false)
    private Long originId;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private String completedBy;

    @Column(name = "completion_note")
    private String completionNote;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected Task() {
    }

    public Task(Long enrolmentId, Long memberId, String taskType, String title, String description, short priority,
                String assignedToActor, String assignedToRole, Instant dueAt, String originType, Long originId) {
        this.enrolmentId = enrolmentId;
        this.memberId = memberId;
        this.taskType = taskType;
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.assignedToActor = assignedToActor;
        this.assignedToRole = assignedToRole;
        this.dueAt = dueAt;
        this.originType = originType;
        this.originId = originId;
    }

    public boolean isOpen() {
        return "assigned".equals(status) || "accepted".equals(status) || "in_progress".equals(status)
                || "blocked".equals(status);
    }

    public void complete(String by, String note, Instant at) {
        this.status = "completed";
        this.completedBy = by;
        this.completionNote = note;
        this.completedAt = at;
    }

    public void cancel(String reason, Instant at) {
        this.status = "cancelled";
        this.cancelReason = reason;
        this.cancelledAt = at;
    }

    public Long getEnrolmentId() {
        return enrolmentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Short getPriority() {
        return priority;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedToActor() {
        return assignedToActor;
    }

    public String getAssignedToRole() {
        return assignedToRole;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public String getSlaState() {
        return slaState;
    }

    public String getOriginType() {
        return originType;
    }

    public Long getOriginId() {
        return originId;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
    }

    public String getCompletionNote() {
        return completionNote;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
