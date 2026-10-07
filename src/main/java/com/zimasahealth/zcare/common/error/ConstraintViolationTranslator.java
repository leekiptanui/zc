package com.zimasahealth.zcare.common.error;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * Maps a violated database constraint, by its agreed-design name, to a registry code (04D
 * section 9.3). Constraint names are part of the API contract for exactly this reason: renaming
 * one changes what the caller sees (ADR-0003).
 *
 * <p>Services check every rule first; this is the answer when the database, the last line,
 * refuses a write the service let through, typically a race between two requests.
 */
public final class ConstraintViolationTranslator {

    private static final Map<String, ZCareExceptionCode> CODES = Map.ofEntries(
            Map.entry("uq_zc_enrolment_one_active", ZCareExceptionCode.ZCARE_ENROLMENT_DUPLICATE),
            Map.entry("uq_zc_care_gap_dedup", ZCareExceptionCode.ZCARE_GAP_DUPLICATE),
            Map.entry("uq_zc_refill_one_open", ZCareExceptionCode.ZCARE_REFILL_DUPLICATE),
            Map.entry("trg_zc_programme_version_immutable", ZCareExceptionCode.ZCARE_PROGRAMME_VERSION_IMMUTABLE),
            Map.entry("trg_zc_assessment_template_version_immutable",
                    ZCareExceptionCode.ZCARE_PROGRAMME_VERSION_IMMUTABLE),
            Map.entry("ck_zc_programme_version_publication", ZCareExceptionCode.ZCARE_CLINICAL_APPROVAL_REQUIRED),
            Map.entry("ck_zc_programme_version_published_approved",
                    ZCareExceptionCode.ZCARE_CLINICAL_APPROVAL_REQUIRED),
            Map.entry("trg_zc_care_plan_approval_immutable", ZCareExceptionCode.ZCARE_PLAN_VERSION_IMMUTABLE),
            Map.entry("trg_zc_config_history_append_only", ZCareExceptionCode.ZCARE_CONFIG_HISTORY_IMMUTABLE),
            Map.entry("ck_zc_refill_confirmed_requires_authorised_source",
                    ZCareExceptionCode.ZCARE_FULFILMENT_SOURCE_REQUIRED),
            Map.entry("ck_zc_care_gap_action_reason", ZCareExceptionCode.ZCARE_GAP_SUPPRESS_REASON_REQUIRED));

    private ConstraintViolationTranslator() {
    }

    /** The constraint named in a PostgreSQL error anywhere in the cause chain. */
    public static Optional<String> constraintName(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql) {
                ServerErrorMessage message = psql.getServerErrorMessage();
                if (message != null && message.getConstraint() != null) {
                    return Optional.of(message.getConstraint());
                }
            }
            if (t instanceof SQLException sql && sql.getNextException() != null) {
                Optional<String> next = constraintName(sql.getNextException());
                if (next.isPresent()) {
                    return next;
                }
            }
        }
        return Optional.empty();
    }

    /** A business exception for the violated constraint; VALIDATION_FIELD_INVALID when unmapped. */
    public static BusinessException translate(Throwable error) {
        Optional<String> constraint = constraintName(error);
        ZCareExceptionCode code = constraint.map(CODES::get).orElse(ZCareExceptionCode.VALIDATION_FIELD_INVALID);
        String message = constraint
                .map(name -> "The request conflicts with a rule the database enforces (" + name + ")")
                .orElse("The request conflicts with a rule the database enforces");
        return BusinessException.of(code, message).with("constraint", constraint.orElse(null));
    }
}
