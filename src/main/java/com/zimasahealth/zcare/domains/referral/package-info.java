/**
 * Referrals, Transitions and Provider Participation (roadmap milestone M11): referral
 * coordination records (never clinical orders), care transitions, provider participation and
 * append-only provider actions. A referral completes only on provider or clinician
 * confirmation.
 *
 * <p>Owns {@code zc_referral}, {@code zc_transition}, {@code zc_provider_participation} and
 * {@code zc_provider_action}, created by
 * {@code db/changelog/migrations/20260929_11_referral.xml}. Non-Java files of this domain live
 * in {@code src/main/resources/domains/referral/}.
 */
package com.zimasahealth.zcare.domains.referral;
