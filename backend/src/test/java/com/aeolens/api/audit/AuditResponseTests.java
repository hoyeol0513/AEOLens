package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class AuditResponseTests {

    @Test
    void calculatesOverallScoreFromCategoryScores() {
        assertThat(AuditResponse.Scores.from(81, 90))
                .isEqualTo(new AuditResponse.Scores(86, 81, 90));
    }

    @Test
    void rejectsOutOfRangeOrInconsistentScores() {
        assertThatThrownBy(() -> AuditResponse.Scores.from(101, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aeo score");
        assertThatThrownBy(() -> new AuditResponse.Scores(90, 70, 80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rounded average");
    }

    @Test
    void summarizesRuleResultsBySeverity() {
        List<AuditResponse.Issue> issues = List.of(
                issue("schema", "critical", 1),
                issue("alt", "critical", 4),
                issue("heading", "warning", 1),
                issue("landmarks", "passed", 1));

        assertThat(AuditResponse.IssueSummary.from(issues))
                .isEqualTo(new AuditResponse.IssueSummary(4, 3, 2, 1, 1));
    }

    private AuditResponse.Issue issue(String id, String severity, int count) {
        return new AuditResponse.Issue(id, severity, id, id, count, id, id);
    }
}
