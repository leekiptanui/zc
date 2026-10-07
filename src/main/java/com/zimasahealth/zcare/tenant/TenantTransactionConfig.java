package com.zimasahealth.zcare.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Replaces Spring Boot's JPA transaction manager with the tenant-aware one. */
@Configuration(proxyBeanMethods = false)
public class TenantTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new TenantAwareTransactionManager(emf);
    }
}
