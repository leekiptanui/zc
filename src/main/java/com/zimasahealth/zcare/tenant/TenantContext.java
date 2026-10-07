package com.zimasahealth.zcare.tenant;

import java.util.Optional;

/**
 * Holds the tenant of the current request thread. Set once the URL tenant and the token tenant
 * agree, and cleared in a {@code finally} block by the filter that set it (CODE-07).
 */
public final class TenantContext {

    private static final ThreadLocal<CurrentTenant> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(CurrentTenant tenant) {
        CURRENT.set(tenant);
    }

    public static Optional<CurrentTenant> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** The current tenant; fails when none is set, because every tenant table would read empty. */
    public static CurrentTenant require() {
        CurrentTenant tenant = CURRENT.get();
        if (tenant == null) {
            throw new IllegalStateException("No tenant in context: row-level security would hide every row");
        }
        return tenant;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
