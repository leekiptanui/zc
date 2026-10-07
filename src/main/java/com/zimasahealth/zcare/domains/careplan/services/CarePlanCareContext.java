package com.zimasahealth.zcare.domains.careplan.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import com.zimasahealth.zcare.domains.careplan.entities.CarePlan;
import com.zimasahealth.zcare.domains.careplan.mappers.CarePlanMapper;
import com.zimasahealth.zcare.domains.careplan.repositories.CarePlanRepository;
import com.zimasahealth.zcare.domains.careplan.repositories.GoalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The active plan with its goals, and any plan in preparation. */
@Component
@Transactional(readOnly = true)
public class CarePlanCareContext implements CareContextContributor {

    private final CarePlanRepository plans;
    private final GoalRepository goals;
    private final CarePlanMapper mapper;

    public CarePlanCareContext(CarePlanRepository plans, GoalRepository goals, CarePlanMapper mapper) {
        this.plans = plans;
        this.goals = goals;
        this.mapper = mapper;
    }

    @Override
    public String section() {
        return "carePlan";
    }

    @Override
    public Object contribute(long enrolmentId) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("activePlan", plans.findFirstByEnrolmentIdAndStatus(enrolmentId, "active").map(this::summary)
                .orElse(null));
        section.put("planInPreparation", plans.findFirstByEnrolmentIdAndStatusIn(enrolmentId,
                List.of("draft", "pending_approval")).map(this::summary).orElse(null));
        return section;
    }

    private Map<String, Object> summary(CarePlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("carePlanId", plan.getId());
        summary.put("versionNumber", plan.getVersionNumber());
        summary.put("status", plan.getStatus());
        summary.put("activatedAt", plan.getActivatedAt());
        summary.put("goals", goals.findByCarePlanIdOrderById(plan.getId()).stream().map(mapper::toView).toList());
        return summary;
    }
}
