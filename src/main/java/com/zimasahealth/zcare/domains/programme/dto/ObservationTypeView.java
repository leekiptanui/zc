package com.zimasahealth.zcare.domains.programme.dto;

import java.math.BigDecimal;

public record ObservationTypeView(Long id, Long programmeVersionId, String code, String name, String unit,
                                  String loincCode, BigDecimal plausibleMin, BigDecimal plausibleMax) {
}
