package com.zimasahealth.zcare.domains.observation.services;

import com.zimasahealth.zcare.common.carecontext.CareContextContributor;
import org.springframework.stereotype.Component;

/** The enrolment's ten most recent valid readings. */
@Component
public class ObservationCareContext implements CareContextContributor {

    private final ObservationService observations;

    public ObservationCareContext(ObservationService observations) {
        this.observations = observations;
    }

    @Override
    public String section() {
        return "recentObservations";
    }

    @Override
    public Object contribute(long enrolmentId) {
        return observations.latest(enrolmentId);
    }
}
