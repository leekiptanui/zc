package com.zimasahealth.zcare.domains.programme.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A programme version as other domains see it.
 *
 * @param consentWordingVersion the wording consent must match before activation; null when unset
 */
public record ProgrammeVersionView(Long id, Long programmeId, Integer versionNumber, String status,
                                   String consentWordingVersion) {

    @JsonIgnore
    public boolean isPublished() {
        return "published".equals(status);
    }
}
