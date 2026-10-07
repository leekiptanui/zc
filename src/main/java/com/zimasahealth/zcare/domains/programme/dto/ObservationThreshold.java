package com.zimasahealth.zcare.domains.programme.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Review bounds for one observation type; either may be null. */
public record ObservationThreshold(BigDecimal reviewAbove, BigDecimal reviewBelow) {

    @JsonIgnore
    public boolean isEvaluable() {
        return reviewAbove != null || reviewBelow != null;
    }

    public boolean isBreachedBy(BigDecimal value) {
        return (reviewAbove != null && value.compareTo(reviewAbove) > 0)
                || (reviewBelow != null && value.compareTo(reviewBelow) < 0);
    }
}
