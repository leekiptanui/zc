package com.zimasahealth.zcare.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zimasahealth.zcare.common.context.RequestCorrelation;
import com.zimasahealth.zcare.common.error.ZCareExceptionCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void clear() {
        RequestCorrelation.clear();
    }

    @Test
    void serialisesTheEightEnvelopeFieldsInOrder() throws Exception {
        RequestCorrelation.set("req-1", "corr-1");
        ApiResponse<Map<String, Object>> response = ApiResponse.success(Map.<String, Object>of("enrolmentId", 7))
                .nextActions("create_care_plan").session("enrolmentId", 7);
        response.complete("ACTIVATE_ENROLMENT");

        JsonNode node = json.readTree(json.writeValueAsString(response));

        assertThat(node.fieldNames()).toIterable().containsExactly("requestId", "timestamp", "status", "data",
                "exceptions", "nextActions", "sessionContext", "auditTrail");
        assertThat(node.path("status").asText()).isEqualTo("success");
        assertThat(node.path("requestId").asText()).isEqualTo("req-1");
        assertThat(node.path("sessionContext").path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(node.path("auditTrail").path("operation").asText()).isEqualTo("ACTIVATE_ENROLMENT");
    }

    @Test
    void aWarningTurnsTheEnvelopeIntoAnExceptionAndKeepsTheData() {
        ServiceResult<String> result = ServiceResult.of("held")
                .next("proceed")
                .warning(ZCareExceptionCode.ZCARE_OUTREACH_FREQUENCY_EXCEEDED, "Held", Map.of());

        ApiResponse<String> response = ApiResponse.from(result);

        assertThat(response.getStatus()).isEqualTo(EnvelopeStatus.EXCEPTION);
        assertThat(response.getData()).isEqualTo("held");
        assertThat(response.getNextActions()).containsExactly("proceed");
        assertThat(response.getExceptions()).extracting(ApiException::severity).containsExactly(Severity.WARNING);
    }

    @Test
    void hardStopsCarryNoNextActions() {
        assertThat(List.of(ZCareExceptionCode.values()))
                .filteredOn(code -> code.severity() == Severity.HARD_STOP)
                .allSatisfy(code -> assertThat(code.nextActions()).isEmpty());
    }
}
