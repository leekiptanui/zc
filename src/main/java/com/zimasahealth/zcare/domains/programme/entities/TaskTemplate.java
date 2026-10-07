package com.zimasahealth.zcare.domains.programme.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_task_template}: a kind of task a programme version can raise, with its defaults. */
@Entity
@Table(name = "zc_task_template")
@DynamicUpdate
public class TaskTemplate extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "task_type", nullable = false, updatable = false)
    private String taskType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "default_priority", nullable = false)
    private Short defaultPriority;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "default_assignee_role")
    private String defaultAssigneeRole;

    protected TaskTemplate() {
    }

    public TaskTemplate(Long programmeVersionId, String taskType) {
        this.programmeVersionId = programmeVersionId;
        this.taskType = taskType;
    }

    public TaskTemplate copyTo(Long versionId) {
        TaskTemplate copy = new TaskTemplate(versionId, taskType);
        copy.update(name, description, defaultPriority, slaHours, defaultAssigneeRole);
        return copy;
    }

    public void update(String name, String description, Short defaultPriority, Integer slaHours,
                       String defaultAssigneeRole) {
        this.name = name;
        this.description = description;
        this.defaultPriority = defaultPriority;
        this.slaHours = slaHours;
        this.defaultAssigneeRole = defaultAssigneeRole;
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Short getDefaultPriority() {
        return defaultPriority;
    }

    public Integer getSlaHours() {
        return slaHours;
    }

    public String getDefaultAssigneeRole() {
        return defaultAssigneeRole;
    }
}
