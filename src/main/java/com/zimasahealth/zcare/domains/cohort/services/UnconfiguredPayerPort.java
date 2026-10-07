package com.zimasahealth.zcare.domains.cohort.services;

import org.springframework.stereotype.Component;

/**
 * Stands in until a payer adapter exists (M05/M06). It never pretends to find nobody: an
 * identification with no payer connected is reported as the payer being unavailable.
 */
@Component
public class UnconfiguredPayerPort implements PayerPort {

    @Override
    public Identification identify(long programmeVersionId, String ruleReference) {
        throw new PayerUnavailableException("No payer adapter is configured for this tenant");
    }
}
