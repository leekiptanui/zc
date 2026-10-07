package com.zimasahealth.zcare.domains.carework.services;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.zimasahealth.zcare.common.context.ZcareRole;

/** Task types the service itself raises, and the rules attached to them. */
public final class TaskTypes {

    public static final String PLAN_APPROVAL = "PLAN_APPROVAL";
    public static final String CARE_PLAN_INTERVENTION = "CARE_PLAN_INTERVENTION";
    public static final String THRESHOLD_REVIEW = "THRESHOLD_REVIEW";
    public static final String SAFE_EXIT = "SAFE_EXIT";

    /** Only this role may complete the type; anyone else gets ZCARE_TASK_RESTRICTED (UC-ZC-003 E1). */
    private static final Map<String, ZcareRole> RESTRICTED_TO = Map.of(THRESHOLD_REVIEW, ZcareRole.CLINICIAN);

    /** Closed by the clinician's decision on the plan, never by {@code :complete}. */
    static final Set<String> CLOSED_BY_DECISION = Set.of(PLAN_APPROVAL);

    /** Completable after consent is withdrawn: the safe-exit follow-up exists because of it. */
    static final Set<String> CONSENT_EXEMPT = Set.of(SAFE_EXIT);

    private TaskTypes() {
    }

    static Optional<ZcareRole> restrictedTo(String taskType) {
        return Optional.ofNullable(RESTRICTED_TO.get(taskType));
    }
}
