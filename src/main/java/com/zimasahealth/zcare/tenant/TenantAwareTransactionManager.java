package com.zimasahealth.zcare.tenant;

import java.sql.PreparedStatement;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Starts every transaction with {@code set_config('zcare.tenant_id', <id>, true)} when a tenant
 * is in context, so row-level security admits exactly that tenant's rows (04F section 9). The
 * setting is transaction-local: PostgreSQL discards it at commit or rollback, so a pooled
 * connection never carries one tenant into another request.
 *
 * <p>Without a tenant in context nothing is set and every tenant table reads empty: fail closed.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    public TenantAwareTransactionManager(EntityManagerFactory emf) {
        super(emf);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        TenantContext.current().ifPresent(tenant -> {
            EntityManagerHolder holder = (EntityManagerHolder) TransactionSynchronizationManager
                    .getResource(obtainEntityManagerFactory());
            EntityManager em = holder.getEntityManager();
            em.unwrap(Session.class).doWork(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT set_config('zcare.tenant_id', ?, true)")) {
                    statement.setString(1, Long.toString(tenant.id()));
                    statement.execute();
                }
            });
        });
    }
}
