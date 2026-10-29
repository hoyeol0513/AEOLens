package com.aeolens.api.history;

import java.time.Instant;
import java.util.UUID;

import com.aeolens.api.audit.AuditResponse;

public record AuditSummary(
        UUID auditId,
        String url,
        Instant auditedAt,
        AuditResponse.Scores scores,
        String crawlMode,
        boolean csrLikely,
        int issueCount
) {
}
