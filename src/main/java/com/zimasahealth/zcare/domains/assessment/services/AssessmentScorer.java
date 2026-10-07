package com.zimasahealth.zcare.domains.assessment.services;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic scoring (ADR-007: no AI). The same answers always give the same score and tier
 * (M08 done-when). Rules come from the template version:
 * <pre>
 * {"points": {"q1": {"yes": 3, "no": 0}},
 *  "tiers":  [{"tier": "high_priority", "min": 8}, {"tier": "standard", "min": 0}]}
 * </pre>
 * The first tier whose {@code min} (and optional {@code max}) the score meets is chosen.
 */
public final class AssessmentScorer {

    private AssessmentScorer() {
    }

    /** @return the score, the per-question points, and the tier, which is null when no tier matches */
    public static Score score(Map<String, Object> rules, Map<String, String> answers) {
        Map<String, Object> perQuestion = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        Object points = rules.get("points");
        if (points instanceof Map<?, ?> table) {
            for (Map.Entry<String, String> answer : answers.entrySet()) {
                if (table.get(answer.getKey()) instanceof Map<?, ?> options
                        && options.get(answer.getValue()) != null) {
                    BigDecimal value = decimal(options.get(answer.getValue()));
                    perQuestion.put(answer.getKey(), value);
                    total = total.add(value);
                }
            }
        }
        String tier = null;
        if (rules.get("tiers") instanceof List<?> tiers) {
            for (Object entry : tiers) {
                if (entry instanceof Map<?, ?> band && band.get("tier") instanceof String name
                        && meets(total, band.get("min"), band.get("max"))) {
                    tier = name;
                    break;
                }
            }
        }
        return new Score(total, perQuestion, tier);
    }

    private static boolean meets(BigDecimal score, Object min, Object max) {
        boolean aboveMin = min == null || score.compareTo(decimal(min)) >= 0;
        boolean belowMax = max == null || score.compareTo(decimal(max)) <= 0;
        return aboveMin && belowMax;
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString().trim());
    }

    public record Score(BigDecimal total, Map<String, Object> perQuestion, String tier) {
    }
}
