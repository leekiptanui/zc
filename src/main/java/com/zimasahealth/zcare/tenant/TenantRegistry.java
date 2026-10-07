package com.zimasahealth.zcare.tenant;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves a tenant code to its {@code zc_tenant} row. {@code zc_tenant} has no row-level security,
 * so this lookup runs before any tenant is set. Results are cached briefly; a suspended tenant
 * stops resolving within {@link #CACHE_TTL}.
 */
@Component
public class TenantRegistry {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public TenantRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The active tenant with this code, or empty when it is unknown, suspended or closed. */
    public Optional<CurrentTenant> findActive(String code) {
        Cached cached = cache.get(code);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.tenant();
        }
        List<CurrentTenant> rows = jdbc.query(
                "SELECT id, code FROM zc_tenant WHERE code = ? AND status = 'active'",
                (rs, i) -> new CurrentTenant(rs.getLong("id"), rs.getString("code")),
                code);
        Optional<CurrentTenant> tenant = rows.stream().findFirst();
        cache.put(code, new Cached(tenant, Instant.now().plus(CACHE_TTL)));
        return tenant;
    }

    private record Cached(Optional<CurrentTenant> tenant, Instant expiresAt) {
    }
}
