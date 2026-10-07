package com.zimasahealth.zcare.common.audit;

import java.util.Map;

/**
 * One {@code zc_domain_audit} row: who did which operation to which record, and why. States are
 * small snapshots of the fields that changed, never whole records (04D section 15).
 */
public final class AuditEntry {

    private final String operation;
    private final String entityType;
    private final long entityId;
    private Long memberId;
    private Long programmeVersionId;
    private Long organisationId;
    private Map<String, Object> previousState;
    private Map<String, Object> newState;
    private String reason;
    private String consentWordingVersion;
    private String consentChannel;

    private AuditEntry(String operation, String entityType, long entityId) {
        this.operation = operation;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /** @param operation must equal the endpoint's {@code @AuditOperation} */
    public static AuditEntry of(String operation, String entityType, long entityId) {
        return new AuditEntry(operation, entityType, entityId);
    }

    public AuditEntry member(Long memberId) {
        this.memberId = memberId;
        return this;
    }

    public AuditEntry programmeVersion(Long programmeVersionId) {
        this.programmeVersionId = programmeVersionId;
        return this;
    }

    public AuditEntry organisation(Long organisationId) {
        this.organisationId = organisationId;
        return this;
    }

    public AuditEntry previousState(Map<String, Object> previousState) {
        this.previousState = previousState;
        return this;
    }

    public AuditEntry newState(Map<String, Object> newState) {
        this.newState = newState;
        return this;
    }

    public AuditEntry reason(String reason) {
        this.reason = reason;
        return this;
    }

    public AuditEntry consent(String wordingVersion, String channel) {
        this.consentWordingVersion = wordingVersion;
        this.consentChannel = channel;
        return this;
    }

    String operation() {
        return operation;
    }

    String entityType() {
        return entityType;
    }

    long entityId() {
        return entityId;
    }

    Long memberId() {
        return memberId;
    }

    Long programmeVersionId() {
        return programmeVersionId;
    }

    Long organisationId() {
        return organisationId;
    }

    Map<String, Object> previousState() {
        return previousState;
    }

    Map<String, Object> newState() {
        return newState;
    }

    String reason() {
        return reason;
    }

    String consentWordingVersion() {
        return consentWordingVersion;
    }

    String consentChannel() {
        return consentChannel;
    }
}
