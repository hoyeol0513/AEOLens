package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

class PlaywrightDynamicPageAnalyzerE2eTests {

    @Test
    @EnabledIfEnvironmentVariable(named = "PLAYWRIGHT_E2E", matches = "true")
    void rendersPageAndCollectsVisualMetricsWithRealChromium() throws Exception {
        String html = """
                <html lang="en">
                  <head><style>
                    body { background: white; }
                    p { color: #aaa; background: white; }
                    button { width: 20px; height: 20px; }
                  </style></head>
                  <body><main><h1>Rendered page</h1><p>Low contrast text</p><button>Go</button></main></body>
                </html>
                """;
        DisposableServer server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .route(routes -> routes.get("/", (request, response) -> response
                        .header("Content-Type", "text/html; charset=UTF-8")
                        .sendString(Mono.just(html))))
                .bindNow();

        try {
            URI target = URI.create("http://127.0.0.1:" + server.port() + "/");
            TargetUrlValidator localTestValidator = new TargetUrlValidator() {
                @Override
                URI validateBlocking(String rawUrl) {
                    return URI.create(rawUrl);
                }
            };
            Class<?> analyzerType = Class.forName("com.aeolens.api.audit.PlaywrightDynamicPageAnalyzer");
            DynamicPageAnalyzer analyzer = (DynamicPageAnalyzer) analyzerType
                    .getConstructor(TargetUrlValidator.class, Duration.class, Duration.class)
                    .newInstance(localTestValidator, Duration.ofSeconds(10), Duration.ofSeconds(2));

            RenderedPage renderedPage = analyzer.analyze(target);

            assertThat(renderedPage.finalUri()).isEqualTo(target);
            assertThat(renderedPage.html()).contains("Rendered page", "Low contrast text");
            assertThat(renderedPage.insufficientContrast()).isGreaterThanOrEqualTo(1);
            assertThat(renderedPage.smallTouchTargets()).isGreaterThanOrEqualTo(1);
        } finally {
            server.disposeNow();
        }
    }
}
