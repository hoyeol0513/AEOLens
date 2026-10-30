package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

class AuditServiceTests {

    @Test
    void runsBrowserAnalysisForServerRenderedPagesWhenDynamicAnalyzerIsAvailable() {
        DisposableServer server = htmlServer("""
                <html lang="en"><body>
                  <header></header><main><article><h1>Static answer</h1><p>%s</p></article></main>
                  <script type="application/ld+json">{"@type":"Article"}</script>
                </body></html>
                """.formatted("Useful static content. ".repeat(30)));
        URI target = URI.create("http://127.0.0.1:" + server.port());
        AtomicInteger browserRuns = new AtomicInteger();
        DynamicPageAnalyzer dynamicAnalyzer = uri -> {
            browserRuns.incrementAndGet();
            return new RenderedPage(uri, """
                    <html lang="en"><body>
                      <header></header><main><article><h1>Rendered answer</h1><p>%s</p></article></main>
                      <script type="application/ld+json">{"@type":"Article"}</script>
                    </body></html>
                    """.formatted("Useful rendered content. ".repeat(30)), 1, 1);
        };

        try {
            AuditResponse response = service(target, dynamicAnalyzer)
                    .analyze("https://example.com")
                    .block(Duration.ofSeconds(5));

            assertThat(response).isNotNull();
            assertThat(browserRuns).hasValue(1);
            assertThat(response.crawl().mode()).isEqualTo("dynamic");
            assertThat(response.crawl().csrLikely()).isFalse();
            assertThat(response.issues()).extracting(AuditResponse.Issue::id)
                    .contains("contrast", "touch-target")
                    .doesNotContain("dynamic-visual");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void keepsStaticReportWhenBrowserAnalysisFails() {
        DisposableServer server = htmlServer("<html lang=\"en\"><body><main><h1>Answer</h1></main></body></html>");
        URI target = URI.create("http://127.0.0.1:" + server.port());
        DynamicPageAnalyzer failingAnalyzer = uri -> {
            throw new IllegalStateException("Chromium unavailable");
        };

        try {
            AuditResponse response = service(target, failingAnalyzer)
                    .analyze("https://example.com")
                    .block(Duration.ofSeconds(5));

            assertThat(response).isNotNull();
            assertThat(response.crawl().mode()).isEqualTo("static");
            assertThat(response.issues())
                    .filteredOn(issue -> issue.id().equals("dynamic-visual"))
                    .singleElement()
                    .satisfies(issue -> assertThat(issue.description())
                            .contains("Browser-based visual analysis was attempted"));
        } finally {
            server.disposeNow();
        }
    }

    private AuditService service(URI target, DynamicPageAnalyzer dynamicAnalyzer) {
        JsonMapper objectMapper = JsonMapper.builder().build();
        TargetUrlValidator targetUrlValidator = new TargetUrlValidator() {
            @Override
            public Mono<URI> validate(String rawUrl) {
                return Mono.just(target);
            }
        };
        ObjectProvider<DynamicPageAnalyzer> provider = new ObjectProvider<>() {
            @Override
            public DynamicPageAnalyzer getIfAvailable() {
                return dynamicAnalyzer;
            }
        };
        GeminiAssessmentService gemini = new GeminiAssessmentService(
                objectMapper,
                "",
                "test-model",
                "http://127.0.0.1",
                Duration.ofSeconds(1),
                1000);
        return new AuditService(
                targetUrlValidator,
                new StaticAuditAnalyzer(objectMapper),
                provider,
                gemini,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                1_000_000);
    }

    private DisposableServer htmlServer(String html) {
        return HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/", (request, response) -> response
                        .header("Content-Type", "text/html; charset=UTF-8")
                        .sendString(Mono.just(html))))
                .bindNow();
    }
}
