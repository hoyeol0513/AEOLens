package com.aeolens.api.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditResponse(
        UUID auditId,
        String url,
        Instant auditedAt,
        int scannedPages,
        Scores scores,
        CrawlSummary crawl,
        AiAssessment ai,
        List<Issue> issues
) {
    public record Scores(int overall, int aeo, int a11y) {
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

    public record Issue(
            String id,
            String severity,
            String title,
            String description,
            int count,
            String selector,
            String fix
    ) {
    }
}
