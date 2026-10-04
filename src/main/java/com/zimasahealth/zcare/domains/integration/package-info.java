/**
 * Integration Persistence (roadmap milestone M18): idempotency keys, the transactional outbox
 * and inbox, and dead letters. Every delivery that finally fails becomes a visible task, never
 * a log line alone.
 *
 * <p>Owns {@code zc_idempotency_key}, {@code zc_integration_outbox},
 * {@code zc_integration_inbox} and {@code zc_dead_letter}, created by
 * {@code db/changelog/migrations/20260929_19_integration.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/integration/}.
 */
package com.zimasahealth.zcare.domains.integration;
