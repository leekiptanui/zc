package com.zimasahealth.zcare.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintViolationTranslatorTest {

    @Test
    void mapsANamedConstraintToItsRegistryCode() {
        BusinessException translated = ConstraintViolationTranslator.translate(violation("uq_zc_enrolment_one_active"));

        assertThat(translated.code()).isEqualTo(ZCareExceptionCode.ZCARE_ENROLMENT_DUPLICATE);
        assertThat(translated.context()).containsEntry("constraint", "uq_zc_enrolment_one_active");
    }

    @Test
    void mapsTriggerRaisedViolationsByTheirConstraintName() {
        assertThat(ConstraintViolationTranslator.translate(violation("trg_zc_programme_version_immutable")).code())
                .isEqualTo(ZCareExceptionCode.ZCARE_PROGRAMME_VERSION_IMMUTABLE);
    }

    @Test
    void fallsBackToAnInvalidFieldForUnmappedConstraints() {
        assertThat(ConstraintViolationTranslator.translate(violation("ck_zc_goal_measurable")).code())
                .isEqualTo(ZCareExceptionCode.VALIDATION_FIELD_INVALID);
    }

    private static DataIntegrityViolationException violation(String constraint) {
        ServerErrorMessage message = new ServerErrorMessage("SERROR\0C23505\0Mviolation\0n" + constraint + "\0");
        return new DataIntegrityViolationException("refused", new PSQLException(message));
    }
}
