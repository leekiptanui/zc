package com.zimasahealth.zcare.domains.programme.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_goal_type}: a kind of care-plan goal a programme version allows. */
@Entity
@Table(name = "zc_goal_type")
@DynamicUpdate
public class GoalType extends TenantEntity {

    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private Long programmeVersionId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    protected GoalType() {
    }

    public GoalType(Long programmeVersionId, String code, String name) {
        this.programmeVersionId = programmeVersionId;
        this.code = code;
        this.name = name;
    }

    public GoalType copyTo(Long versionId) {
        return new GoalType(versionId, code, name);
    }

    public Long getProgrammeVersionId() {
        return programmeVersionId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
