package com.zimasahealth.zcare.domains.assessment.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AssessmentScorerTest {

    private static final Map<String, Object> RULES = Map.of(
            "points", Map.of("q1", Map.of("yes", 5, "no", 0), "q2", Map.of("yes", 4, "no", 0)),
            "tiers", List.of(Map.of("tier", "high_priority", "min", 8),
                    Map.of("tier", "medium_priority", "min", 4, "max", 7),
                    Map.of("tier", "standard", "min", 0)));

    @Test
    void sumsConfiguredPointsAndPicksTheFirstMatchingTier() {
        AssessmentScorer.Score score = AssessmentScorer.score(RULES, Map.of("q1", "yes", "q2", "yes"));

        assertThat(score.total()).isEqualByComparingTo(BigDecimal.valueOf(9));
        assertThat(score.tier()).isEqualTo("high_priority");
        assertThat(score.perQuestion()).containsKeys("q1", "q2");
    }

    @Test
    void givesTheSameScoreForTheSameAnswers() {
        Map<String, String> answers = Map.of("q1", "no", "q2", "yes");

        assertThat(AssessmentScorer.score(RULES, answers)).isEqualTo(AssessmentScorer.score(RULES, answers));
        assertThat(AssessmentScorer.score(RULES, answers).tier()).isEqualTo("medium_priority");
    }

    @Test
    void ignoresAnswersWithoutConfiguredPoints() {
        AssessmentScorer.Score score = AssessmentScorer.score(RULES, Map.of("q1", "maybe", "q9", "yes"));

        assertThat(score.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(score.tier()).isEqualTo("standard");
    }

    @Test
    void leavesTheTierEmptyWithoutTierRules() {
        assertThat(AssessmentScorer.score(Map.of(), Map.of("q1", "yes")).tier()).isNull();
    }
}
