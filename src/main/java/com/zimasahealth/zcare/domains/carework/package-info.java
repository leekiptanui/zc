/**
 * Care Work (roadmap milestone M10): tasks, task dependencies, append-only assignment history
 * and work-queue definitions. A work queue is a query over tasks, not a table of its own.
 *
 * <p>Owns {@code zc_task}, {@code zc_task_dependency}, {@code zc_task_assignment_history} and
 * {@code zc_work_queue_definition}, created by
 * {@code db/changelog/migrations/20260929_10_carework.xml}. Non-Java files of this domain live
 * in {@code src/main/resources/domains/carework/}.
 */
package com.zimasahealth.zcare.domains.carework;
