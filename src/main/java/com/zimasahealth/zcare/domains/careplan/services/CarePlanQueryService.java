package com.zimasahealth.zcare.domains.careplan.services;

import java.util.Collection;

import com.zimasahealth.zcare.domains.careplan.repositories.CarePlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Care-plan figures for other domains. */
@Service
@Transactional(readOnly = true)
public class CarePlanQueryService {

    private final CarePlanRepository plans;

    public CarePlanQueryService(CarePlanRepository plans) {
        this.plans = plans;
    }

    public long countActive(Collection<Long> programmeVersionIds) {
        return programmeVersionIds.isEmpty() ? 0 : plans.countByProgrammeVersionIdInAndStatus(programmeVersionIds,
                "active");
    }
}
