/**
 * The business domains, one package per roadmap milestone from M3 to M19.
 *
 * <p>Each domain package owns one capability (PKG-03) and exactly the tables of one migration file
 * (PKG-06). Inside it, code is arranged by role: {@code controllers}, {@code services},
 * {@code repositories}, {@code entities}, {@code dto}, {@code mappers} and {@code specifications}.
 * All business logic lives in {@code services}. A domain's non-Java files live in
 * {@code src/main/resources/domains/<domain>/}.
 */
package com.zimasahealth.zcare.domains;
