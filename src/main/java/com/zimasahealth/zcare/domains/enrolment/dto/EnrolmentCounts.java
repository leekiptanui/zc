package com.zimasahealth.zcare.domains.enrolment.dto;

/** The consent funnel of one programme, for aggregate proof. */
public record EnrolmentCounts(long invited, long consented, long activated, long withdrawn) {
}
