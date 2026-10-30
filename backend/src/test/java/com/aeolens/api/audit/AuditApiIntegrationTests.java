package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.aeolens.api.history.AuditSummary;

import reactor.core.publisher.Mono;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class AuditApiIntegrationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private AuditService auditService;

    private AuditResponse auditResponse;

    @BeforeEach
    void setUp() {
        databaseClient.sql("DELETE FROM audits").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM guest_sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM guest_users").fetch().rowsUpdated())
                .block();

        auditResponse = new AuditResponse(
                UUID.randomUUID(),
                "https://example.com",
                Instant.parse("2026-10-29T12:00:00Z"),
                1,
                new AuditResponse.Scores(84, 80, 88),
                new AuditResponse.CrawlSummary("dynamic", false, 1200, 5, 1, 1),
                new AuditResponse.AiAssessment("not-configured", null, "AI assessment is disabled."),
                List.of(new AuditResponse.Issue(
                        "heading", "warning", "Heading hierarchy skips levels",
                        "A heading level was skipped.", 1, "h1, h3",
                        "Keep heading levels sequential.")));
        when(auditService.analyze(anyString())).thenReturn(Mono.just(auditResponse));
    }

    @Test
    void completesGuestOwnedAuditLifecycleThroughHttpApi() {
        EntityExchangeResult<AuditResponse> created = webTestClient.post()
                .uri("/api/v1/audits")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"url\":\"https://example.com\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AuditResponse.class)
                .returnResult();

        assertThat(created.getResponseBody()).isEqualTo(auditResponse);
        ResponseCookie ownerCookie = created.getResponseCookies().getFirst("aeolens_guest");
        assertThat(ownerCookie).isNotNull();
        assertThat(ownerCookie.isHttpOnly()).isTrue();
        verify(auditService).analyze("https://example.com");

        HttpCookie ownerRequestCookie = new HttpCookie(ownerCookie.getName(), ownerCookie.getValue());
        EntityExchangeResult<List<AuditSummary>> history = webTestClient.get()
                .uri("/api/v1/audits")
                .cookie(ownerRequestCookie.getName(), ownerRequestCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AuditSummary.class)
                .returnResult();

        assertThat(history.getResponseBody())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.auditId()).isEqualTo(auditResponse.auditId());
                    assertThat(summary.url()).isEqualTo(auditResponse.url());
                    assertThat(summary.scores()).isEqualTo(auditResponse.scores());
                });

        webTestClient.get()
                .uri("/api/v1/audits/{auditId}", auditResponse.auditId())
                .cookie(ownerRequestCookie.getName(), ownerRequestCookie.getValue())
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuditResponse.class)
                .isEqualTo(auditResponse);

        EntityExchangeResult<List<AuditSummary>> otherHistory = webTestClient.get()
                .uri("/api/v1/audits")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AuditSummary.class)
                .returnResult();
        ResponseCookie otherCookie = otherHistory.getResponseCookies().getFirst("aeolens_guest");
        assertThat(otherCookie).isNotNull();

        webTestClient.get()
                .uri("/api/v1/audits/{auditId}", auditResponse.auditId())
                .cookie(otherCookie.getName(), otherCookie.getValue())
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.delete()
                .uri("/api/v1/audits/{auditId}", auditResponse.auditId())
                .cookie(ownerRequestCookie.getName(), ownerRequestCookie.getValue())
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        webTestClient.get()
                .uri("/api/v1/audits/{auditId}", auditResponse.auditId())
                .cookie(ownerRequestCookie.getName(), ownerRequestCookie.getValue())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsInvalidRequestBeforeStartingAudit() {
        webTestClient.post()
                .uri("/api/v1/audits")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"url\":\"  \"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Request validation failed")
                .jsonPath("$.detail").isEqualTo("URL is required");

        verifyNoInteractions(auditService);
    }
}
