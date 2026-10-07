package com.zimasahealth.zcare.common.carecontext;

/**
 * Contributes one section to a member's care context ({@code GET /members/{id}/care-context},
 * 04B section 11). Each domain that holds health context implements it; the composer collects
 * every implementation, so it needs no compile-time link to those domains.
 *
 * <p>Sections are health data: the composer calls {@link #contribute} only after the consent
 * gate has passed.
 */
public interface CareContextContributor {

    /** Key of the section inside {@code healthContext}. */
    String section();

    /** A thin projection of this domain's records for the enrolment. */
    Object contribute(long enrolmentId);
}
