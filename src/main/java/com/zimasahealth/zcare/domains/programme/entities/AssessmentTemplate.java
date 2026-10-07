package com.zimasahealth.zcare.domains.programme.entities;

import com.zimasahealth.zcare.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** {@code zc_assessment_template}: a questionnaire's identity; its content lives in versions. */
@Entity
@Table(name = "zc_assessment_template")
@DynamicUpdate
public class AssessmentTemplate extends TenantEntity {

    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    protected AssessmentTemplate() {
    }

    public AssessmentTemplate(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
