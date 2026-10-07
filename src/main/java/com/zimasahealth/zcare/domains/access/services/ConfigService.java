package com.zimasahealth.zcare.domains.access.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.zimasahealth.zcare.common.api.ServiceResult;
import com.zimasahealth.zcare.common.audit.AuditEntry;
import com.zimasahealth.zcare.common.audit.AuditWriter;
import com.zimasahealth.zcare.common.context.CurrentActor;
import com.zimasahealth.zcare.common.error.BusinessException;
import com.zimasahealth.zcare.common.time.Times;
import com.zimasahealth.zcare.domains.access.dto.ConfigEntryView;
import com.zimasahealth.zcare.domains.access.dto.SetConfigRequest;
import com.zimasahealth.zcare.domains.access.entities.ConfigHistoryEntry;
import com.zimasahealth.zcare.domains.access.mappers.AccessMapper;
import com.zimasahealth.zcare.domains.access.repositories.ConfigHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant settings with history (ZCR-ADM-002): versioned, approved and effective-dated, never
 * edited retrospectively. The platform administrator who records a setting is its approver.
 */
@Service
public class ConfigService {

    private final ConfigHistoryRepository history;
    private final AccessMapper mapper;
    private final AuditWriter audit;

    public ConfigService(ConfigHistoryRepository history, AccessMapper mapper, AuditWriter audit) {
        this.history = history;
        this.mapper = mapper;
        this.audit = audit;
    }

    /** The value in force now, if one was ever set. */
    @Transactional(readOnly = true)
    public Optional<String> currentValue(String scope, String key) {
        return history.findFirstByConfigScopeAndConfigKeyAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                scope, key, Times.now()).map(ConfigHistoryEntry::getConfigValue);
    }

    /** The value in force now, read as a boolean; false when unset. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String scope, String key) {
        return currentValue(scope, key).map(v -> "true".equalsIgnoreCase(v.trim())).orElse(false);
    }

    /** The value in force now, read as a whole number, or the default when unset or unreadable. */
    @Transactional(readOnly = true)
    public int intValue(String scope, String key, int defaultValue) {
        return currentValue(scope, key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public List<ConfigEntryView> inForce() {
        return history.findInForce(Times.now()).stream().map(mapper::toView).toList();
    }

    @Transactional
    public ServiceResult<ConfigEntryView> set(SetConfigRequest request) {
        Instant now = Times.now();
        Instant effectiveFrom = request.effectiveFrom() == null ? now : request.effectiveFrom();
        if (effectiveFrom.isBefore(now.minusSeconds(60))) {
            throw BusinessException.invalid("effectiveFrom",
                    "A setting cannot take effect in the past; history is never rewritten");
        }
        ConfigHistoryEntry entry = history.save(new ConfigHistoryEntry(request.scope().trim(), request.key().trim(),
                request.value(), effectiveFrom, request.changeReason(), CurrentActor.require().id(), now));
        audit.record(AuditEntry.of(AccessOperations.SET_CONFIG, "config_history", entry.getId())
                .newState(Map.of("scope", entry.getConfigScope(), "key", entry.getConfigKey(),
                        "effectiveFrom", effectiveFrom.toString()))
                .reason(request.changeReason()));
        return ServiceResult.of(mapper.toView(entry)).next("proceed").session("configHistoryId", entry.getId());
    }
}
