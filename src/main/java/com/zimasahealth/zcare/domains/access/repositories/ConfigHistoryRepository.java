package com.zimasahealth.zcare.domains.access.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.zimasahealth.zcare.domains.access.entities.ConfigHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConfigHistoryRepository extends JpaRepository<ConfigHistoryEntry, Long> {

    Optional<ConfigHistoryEntry> findFirstByConfigScopeAndConfigKeyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            String configScope, String configKey, Instant at);

    /** The entry in force for every scope and key at the given time. */
    @Query("""
            SELECT c FROM ConfigHistoryEntry c
             WHERE c.effectiveFrom = (SELECT max(d.effectiveFrom) FROM ConfigHistoryEntry d
                                       WHERE d.configScope = c.configScope AND d.configKey = c.configKey
                                         AND d.effectiveFrom <= :at)
             ORDER BY c.configScope, c.configKey
            """)
    List<ConfigHistoryEntry> findInForce(Instant at);
}
