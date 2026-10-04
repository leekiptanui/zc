/**
 * Medication Coordination (roadmap milestone M12): links to the provider's medication plan,
 * refill requests and adherence events. ZCare never prescribes or dispenses, and
 * member-reported fulfilment is never recorded as confirmed.
 *
 * <p>Owns {@code zc_medication_coordination}, {@code zc_refill_request} and
 * {@code zc_adherence_event}, created by
 * {@code db/changelog/migrations/20260929_12_medication.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/medication/}.
 */
package com.zimasahealth.zcare.domains.medication;
