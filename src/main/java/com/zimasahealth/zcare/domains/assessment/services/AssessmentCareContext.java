package com.zimasahealth.zcare.domains.assessment.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import com.zimasahealth.zcare.domains.assessment.repositories.AssessmentRepository;
import com.zimasahealth.zcare.domains.assessment.repositories.RiskClassificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The member's current priority tier and open assessments. */
@Component
@Transactional(readOnly = true)
public class AssessmentCareContext implements CareContextContributor {

    private final AssessmentRepository assessments;
    private final RiskClassificationRepository classifications;

    public AssessmentCareContext(AssessmentRepository assessments, RiskClassificationRepository classifications) {
        this.assessments = assessments;
        this.classifications = classifications;
    }

    @Override
    public String section() {
        return "assessment";
    }

    @Override
    public Object contribute(long enrolmentId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("priority", classifications.findFirstByEnrolmentIdOrderByClassifiedAtDescIdDesc(enrolmentId)
                .map(c -> Map.of("tier", c.getPriorityTier(), "classifiedAt", c.getClassifiedAt(),
                        "derivedFrom", c.getDerivedFrom()))
                .orElse(null));
        section.put("openAssessments", assessments.findByEnrolmentIdAndStatusInOrderByAssignedAtDesc(enrolmentId,
                        List.of("assigned", "in_progress")).stream()
                .map(a -> Map.of("assessmentId", a.getId(), "status", a.getStatus(), "assignedAt", a.getAssignedAt()))
                .toList());
        return section;
    }
}
