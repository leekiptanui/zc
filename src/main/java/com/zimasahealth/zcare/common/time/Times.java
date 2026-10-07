package com.zimasahealth.zcare.common.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Timestamps at PostgreSQL's precision, so a value read back equals the value written. */
public final class Times {

    private Times() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
