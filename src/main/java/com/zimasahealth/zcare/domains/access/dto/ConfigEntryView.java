package com.zimasahealth.zcare.domains.access.dto;

import java.time.Instant;

public record ConfigEntryView(Long id, String configScope, String configKey, String configValue, Instant effectiveFrom,
                              String changeReason, String approvedBy, Instant approvedAt) {
}
