package com.zimasahealth.zcare.domains.engagement.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.zimasahealth.zcare.domains.engagement.entities.OutreachRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutreachRequestRepository extends JpaRepository<OutreachRequest, Long> {

    /** When the member's latest health-content message that counts toward the limit was requested. */
    @Query("""
            SELECT max(o.requestedAt) FROM OutreachRequest o
             WHERE o.memberId = :memberId AND o.contentClass = 'health_content'
               AND o.status IN ('requested', 'queued', 'held', 'dispatched', 'delivered', 'completed')
            """)
    Instant lastHealthOutreach(Long memberId);

    List<OutreachRequest> findByEnrolmentIdAndStatusIn(Long enrolmentId, Collection<String> statuses);
}
