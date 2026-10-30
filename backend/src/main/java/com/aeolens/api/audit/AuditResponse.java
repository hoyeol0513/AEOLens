package com.aeolens.api.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AuditResponse(
        UUID auditId,
        String url,
        Instant auditedAt,
        int scannedPages,
        Scores scores,
        CrawlSummary crawl,
        AiAssessment ai,
        IssueSummary issueSummary,
        List<Issue> issues
) {

    public AuditResponse(
            UUID auditId,
            String url,
            Instant auditedAt,
            int scannedPages,
            Scores scores,
            CrawlSummary crawl,
            AiAssessment ai,
            List<Issue> issues
    ) {
        this(auditId, url, auditedAt, scannedPages, scores, crawl, ai, null, issues);
    }

    public AuditResponse {
        issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        issueSummary = IssueSummary.from(issues);
    }

    public record Scores(int overall, int aeo, int a11y) {

        public Scores {
            validateRange("overall", overall);
            validateRange("aeo", aeo);
            validateRange("a11y", a11y);
            int calculatedOverall = calculateOverall(aeo, a11y);
            if (overall != calculatedOverall) {
                throw new IllegalArgumentException(
                        "Overall score must be the rounded average of AEO and accessibility scores");
            }
        }

        public static Scores from(int aeo, int a11y) {
            return new Scores(calculateOverall(aeo, a11y), aeo, a11y);
        }

        private static int calculateOverall(int aeo, int a11y) {
            return Math.round((aeo + a11y) / 2.0f);
        }

        private static void validateRange(String name, int score) {
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException(name + " score must be between 0 and 100");
            }
        }
    }

    public record CrawlSummary(
            String mode,
            boolean csrLikely,
            int textLength,
            int semanticElements,
            int jsonLdBlocks,
            int validJsonLdBlocks
    ) {
    }

    public record AiAssessment(String status, Integer score, String feedback) {
    }

    public record IssueSummary(int total, int actionable, int critical, int warning, int passed) {

        public static IssueSummary from(List<Issue> issues) {
            int critical = 0;
            int warning = 0;
            int passed = 0;
            for (Issue issue : issues) {
                switch (issue.severity()) {
                    case "critical" -> critical++;
                    case "warning" -> warning++;
                    case "passed" -> passed++;
                    default -> throw new IllegalArgumentException("Unsupported issue severity: " + issue.severity());
                }
            }
            return new IssueSummary(issues.size(), critical + warning, critical, warning, passed);
        }
    }

    public record Issue(
            String id,
            String severity,
            String title,
            String description,
            int count,
            String selector,
            String fix
    ) {

        public Issue {
            if (!List.of("critical", "warning", "passed").contains(severity)) {
                throw new IllegalArgumentException("Unsupported issue severity: " + severity);
            }
            if (count < 0) {
                throw new IllegalArgumentException("Issue count cannot be negative");
            }
        }
    }
}
