/**
 * Engagement and Outreach (roadmap milestone M15): outreach requests, delivery attempts and
 * inbound member messages. Consent is re-checked at the moment of sending, and STOP suppresses
 * all contact.
 *
 * <p>Owns {@code zc_outreach_request}, {@code zc_outreach_delivery} and
 * {@code zc_inbound_message}, created by
 * {@code db/changelog/migrations/20260929_15_engagement.xml}. Non-Java files of this domain
 * live in {@code src/main/resources/domains/engagement/}.
 */
package com.zimasahealth.zcare.domains.engagement;
