package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

class GeminiAssessmentServiceTests {

    @Test
    void mergesStructuredContextualAssessmentWithoutSendingRawHtml() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post("/v1beta/models/test-model:generateContent", (request, response) -> {
                    apiKey.set(request.requestHeaders().get("x-goog-api-key"));
                    return request.receive().aggregate().asString()
                            .flatMap(body -> {
                                requestBody.set(body);
                                response.header("Content-Type", "application/json");
                                return response.sendString(Mono.just("""
                                        {"candidates":[{"content":{"parts":[{"text":"{\\"score\\":80,\\"feedback\\":\\"Lead with a direct answer and cite a primary source.\\"}"}]}}]}
                                        """)).then();
                            });
                }))
                .bindNow();

        try {
            GeminiAssessmentService service = service(server, "test-key");
            AuditResponse original = response();

            AuditResponse assessed = service.assess(
                    original,
                    "<html><body><main>Useful visible answer</main><script>do not expose this</script></body></html>")
                    .block();

            assertThat(assessed.ai().status()).isEqualTo("complete");
            assertThat(assessed.ai().score()).isEqualTo(80);
            assertThat(assessed.ai().feedback()).contains("direct answer");
            assertThat(assessed.scores().aeo()).isEqualTo(66);
            assertThat(assessed.scores().overall()).isEqualTo(78);
            assertThat(apiKey.get()).isEqualTo("test-key");
            assertThat(requestBody.get())
                    .contains("Useful visible answer")
                    .doesNotContain("<html>", "<script>", "do not expose this");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void keepsRuleBasedResultWhenGeminiIsUnavailable() {
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.post("/v1beta/models/test-model:generateContent",
                        (request, response) -> response.status(503).send()))
                .bindNow();

        try {
            GeminiAssessmentService service = service(server, "test-key");
            AuditResponse original = response();

            AuditResponse assessed = service.assess(original, "<main>Answer</main>").block();

            assertThat(assessed.ai().status()).isEqualTo("unavailable");
            assertThat(assessed.ai().score()).isNull();
            assertThat(assessed.scores()).isEqualTo(original.scores());
        } finally {
            server.disposeNow();
        }
    }

    private GeminiAssessmentService service(DisposableServer server, String apiKey) {
        return new GeminiAssessmentService(
                JsonMapper.builder().build(),
                apiKey,
                "test-model",
                "http://127.0.0.1:" + server.port(),
                Duration.ofSeconds(2),
                1000);
    }

    private AuditResponse response() {
        return new AuditResponse(
                UUID.randomUUID(),
                "https://example.com",
                Instant.parse("2026-10-29T00:00:00Z"),
                1,
                new AuditResponse.Scores(75, 60, 90),
                new AuditResponse.CrawlSummary("static", false, 500, 4, 1, 1),
                new AuditResponse.AiAssessment("ready", null, "Pending"),
                List.of(new AuditResponse.Issue(
                        "answer", "warning", "Primary answer is unclear", "Description", 1, "main", "Fix")));
    }
}
