package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StaticAuditAnalyzerTests {

    private static final URI TARGET = URI.create("https://example.com");
    private static final String CSR_SHELL = "<html><body><div id=\"root\"></div></body></html>";

    @Autowired
    private StaticAuditAnalyzer analyzer;

    @Test
    void reportsBrowserRequirementForStaticCsrShell() {
        AuditResponse response = analyzer.analyze(TARGET, CSR_SHELL, false);

        assertThat(response.crawl().mode()).isEqualTo("static");
        assertThat(response.crawl().csrLikely()).isTrue();
        assertThat(response.scores()).isEqualTo(new AuditResponse.Scores(53, 15, 90));
        assertThat(response.issues()).extracting(AuditResponse.Issue::id)
                .contains(
                        "schema",
                        "h1-missing",
                        "document-language",
                        "semantic-structure",
                        "content-depth",
                        "csr-content",
                        "dynamic-visual");
        assertThat(response.issueSummary().actionable()).isEqualTo(response.issues().size());
    }

    @Test
    void usesRenderedDomAndVisualMetricsForDynamicResult() {
        String renderedHtml = """
                <html lang="en"><body>
                  <header></header><main><article><h1>Answer</h1><p>%s</p></article></main>
                  <script type="application/ld+json">{"@type":"Article"}</script>
                </body></html>
                """.formatted("Useful contextual answer. ".repeat(30));
        RenderedPage renderedPage = new RenderedPage(TARGET, renderedHtml, 1, 1);

        AuditResponse response = analyzer.analyzeDynamic(TARGET, CSR_SHELL, renderedPage, false);

        assertThat(response.crawl().mode()).isEqualTo("dynamic");
        assertThat(response.crawl().csrLikely()).isTrue();
        assertThat(response.crawl().textLength()).isGreaterThan(500);
        assertThat(response.scores().a11y()).isEqualTo(94);
        assertThat(response.issues()).extracting(AuditResponse.Issue::id)
                .contains("contrast", "touch-target")
                .doesNotContain("dynamic-visual");
        assertThat(response.issueSummary())
                .isEqualTo(new AuditResponse.IssueSummary(3, 2, 0, 2, 1));
    }
}
