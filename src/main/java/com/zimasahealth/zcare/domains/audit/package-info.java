/**
 * Audit and Security (roadmap milestone M17): the append-only domain audit trail and the
 * append-only security-event sink. Neither can be edited or deleted.
 *
 * <p>Owns {@code zc_domain_audit} and {@code zc_security_event}, created by
 * {@code db/changelog/migrations/20260929_18_audit.xml}. Non-Java files of this domain live in
 * {@code src/main/resources/domains/audit/}.
 */
package com.zimasahealth.zcare.domains.audit;
