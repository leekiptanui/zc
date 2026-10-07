package com.zimasahealth.zcare.common.idempotency;

import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cached envelopes in {@code zc_idempotency_key}, keyed by (tenant, key, actor, route) and kept
 * at least 24 hours (04D section 18). Each call runs in its own short transaction with the
 * tenant set, so row-level security scopes it like any other read.
 */
@Component
public class IdempotencyStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public IdempotencyStore(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** The live cached entry for this key, actor and route, if any. */
    public Optional<CachedResponse> find(String key, String actorId, String route) {
        return transactions.execute(status -> {
            List<CachedResponse> rows = jdbc.query("""
                    SELECT request_body_hash, response_envelope::text
                      FROM zc_idempotency_key
                     WHERE idempotency_key = ? AND actor_id = ? AND route = ?
                       AND status = 'completed' AND expires_at > now()
                    """,
                    (rs, i) -> new CachedResponse(rs.getString(1), rs.getString(2)),
                    key, actorId, route);
            return rows.stream().findFirst();
        });
    }

    /**
     * Stores the envelope. An expired entry for the same key is overwritten, never deleted:
     * {@code zc_app} holds no DELETE, and expired rows are the housekeeping sweep's (OD-25).
     */
    public void save(String key, String actorId, String route, String bodyHash, String envelopeJson) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO zc_idempotency_key (tenant_id, idempotency_key, actor_id, route,
                    request_body_hash, status, response_status, response_envelope, created_by)
                VALUES (?, ?, ?, ?, ?, 'completed', 200, ?::jsonb, ?)
                ON CONFLICT ON CONSTRAINT uq_zc_idempotency_key DO UPDATE
                   SET request_body_hash = EXCLUDED.request_body_hash,
                       response_envelope = EXCLUDED.response_envelope,
                       expires_at = now() + interval '24 hours',
                       updated_by = EXCLUDED.created_by
                 WHERE zc_idempotency_key.expires_at <= now()
                """,
                TenantContext.require().id(), key, actorId, route, bodyHash, envelopeJson, actorId));
    }

    /** A cached envelope and the hash of the request body that produced it. */
    public record CachedResponse(String bodyHash, String envelopeJson) {
    }
}
