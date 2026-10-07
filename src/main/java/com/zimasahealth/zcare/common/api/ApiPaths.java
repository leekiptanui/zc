package com.zimasahealth.zcare.common.api;

/** The versioned base path, defined once (OD-03; ADR-0005). */
public final class ApiPaths {

    /**
     * {@code /api/v1}. The tenant is never part of a URL: it comes only from the verified token,
     * so no tenant identifier appears in addresses, browser history, proxy logs or referrers.
     */
    public static final String BASE = "/api/v1";

    private ApiPaths() {
    }
}
