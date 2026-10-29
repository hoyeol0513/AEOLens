package com.aeolens.api.history;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

import com.aeolens.api.audit.AuditResponse;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditHistoryService {

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;
    private final Duration retention;
    private final int historyLimit;

    public AuditHistoryService(
            DatabaseClient databaseClient,
            ObjectMapper objectMapper,
            @Value("${aeolens.history.retention:90d}") Duration retention,
            @Value("${aeolens.history.limit:20}") int historyLimit
    ) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
        this.retention = retention;
        this.historyLimit = historyLimit;
    }

    public Mono<AuditResponse> save(UUID guestId, AuditResponse response) {
        String resultJson = serialize(response);
        OffsetDateTime auditedAt = OffsetDateTime.ofInstant(response.auditedAt(), ZoneOffset.UTC);
        OffsetDateTime expiresAt = auditedAt.plus(retention);

        return databaseClient.sql("""
                        INSERT INTO audits (
                            id, guest_id, target_url, audited_at, scanned_pages,
                            overall_score, aeo_score, a11y_score, crawl_mode,
                            csr_likely, issue_count, result_json, expires_at
                        ) VALUES (
                            :id, :guestId, :targetUrl, :auditedAt, :scannedPages,
                            :overallScore, :aeoScore, :a11yScore, :crawlMode,
                            :csrLikely, :issueCount, :resultJson, :expiresAt
                        )
                        """)
                .bind("id", response.auditId())
                .bind("guestId", guestId)
                .bind("targetUrl", response.url())
                .bind("auditedAt", auditedAt)
                .bind("scannedPages", response.scannedPages())
                .bind("overallScore", response.scores().overall())
                .bind("aeoScore", response.scores().aeo())
                .bind("a11yScore", response.scores().a11y())
                .bind("crawlMode", response.crawl().mode())
                .bind("csrLikely", response.crawl().csrLikely())
                .bind("issueCount", response.issues().size())
                .bind("resultJson", resultJson)
                .bind("expiresAt", expiresAt)
                .fetch()
                .rowsUpdated()
                .thenReturn(response);
    }

    public Mono<List<AuditSummary>> list(UUID guestId) {
        return databaseClient.sql("""
                        SELECT id, target_url, audited_at, overall_score, aeo_score,
                               a11y_score, crawl_mode, csr_likely, issue_count
                        FROM audits
                        WHERE guest_id = :guestId
                          AND expires_at > CURRENT_TIMESTAMP
                        ORDER BY audited_at DESC
                        LIMIT :historyLimit
                        """)
                .bind("guestId", guestId)
                .bind("historyLimit", historyLimit)
                .map((row, metadata) -> new AuditSummary(
                        row.get("id", UUID.class),
                        row.get("target_url", String.class),
                        row.get("audited_at", OffsetDateTime.class).toInstant(),
                        new AuditResponse.Scores(
                                row.get("overall_score", Integer.class),
                                row.get("aeo_score", Integer.class),
                                row.get("a11y_score", Integer.class)),
                        row.get("crawl_mode", String.class),
                        Boolean.TRUE.equals(row.get("csr_likely", Boolean.class)),
                        row.get("issue_count", Integer.class)))
                .all()
                .collectList();
    }

    public Mono<AuditResponse> find(UUID guestId, UUID auditId) {
        return databaseClient.sql("""
                        SELECT result_json
                        FROM audits
                        WHERE id = :auditId
                          AND guest_id = :guestId
                          AND expires_at > CURRENT_TIMESTAMP
                        """)
                .bind("auditId", auditId)
                .bind("guestId", guestId)
                .map((row, metadata) -> deserialize(row.get("result_json", String.class)))
                .one();
    }

    public Mono<Boolean> delete(UUID guestId, UUID auditId) {
        return databaseClient.sql("""
                        DELETE FROM audits
                        WHERE id = :auditId AND guest_id = :guestId
                        """)
                .bind("auditId", auditId)
                .bind("guestId", guestId)
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    private String serialize(AuditResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize the audit result", exception);
        }
    }

    private AuditResponse deserialize(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AuditResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not deserialize the audit result", exception);
        }
    }
}
