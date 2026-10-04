/**
 * AI decision support, switched off (roadmap milestone M19): advisory recommendations and
 * their human reviews. No AI is called, and every recommendation is advisory by constraint.
 *
 * <p>Owns {@code zc_ai_recommendation} and {@code zc_ai_review}, created by
 * {@code db/changelog/migrations/20260929_17_ai.xml}. Non-Java files of this domain live in
 * {@code src/main/resources/domains/ai/}.
 */
package com.zimasahealth.zcare.domains.ai;
