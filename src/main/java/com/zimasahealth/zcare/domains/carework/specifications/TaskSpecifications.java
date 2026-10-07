package com.zimasahealth.zcare.domains.carework.specifications;

import java.util.List;

import com.zimasahealth.zcare.domains.carework.entities.Task;
import org.springframework.data.jpa.domain.Specification;

/** Work-queue filters: a queue is a live query over tasks, not a stored relationship. */
public final class TaskSpecifications {

    public static final List<String> OPEN = List.of("assigned", "accepted", "in_progress", "blocked");

    private TaskSpecifications() {
    }

    public static Specification<Task> isOpen() {
        return (root, query, cb) -> root.get("status").in(OPEN);
    }

    /** Assigned to the role, or personally to the actor. */
    public static Specification<Task> inQueueOf(String role, String actorId) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("assignedToRole"), role),
                cb.equal(root.get("assignedToActor"), actorId));
    }
}
