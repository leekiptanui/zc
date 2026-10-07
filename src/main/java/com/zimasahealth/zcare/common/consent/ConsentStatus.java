package com.zimasahealth.zcare.common.consent;

import java.util.Set;

/**
 * The consent position of one enrolment.
 *
 * @param scope content classes the consent in force covers; empty when none is in force
 */
public record ConsentStatus(long enrolmentId, ConsentState state, String wordingVersion, Set<ContentClass> scope) {

    public ConsentStatus {
        scope = Set.copyOf(scope);
    }

    /** True when a validated consent, possibly scope-reduced, covers this content class. */
    public boolean covers(ContentClass contentClass) {
        return (state == ConsentState.VALIDATED || state == ConsentState.SCOPE_REDUCED) && scope.contains(contentClass);
    }
}
