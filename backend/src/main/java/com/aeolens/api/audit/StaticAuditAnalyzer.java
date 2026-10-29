package com.aeolens.api.audit;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public class StaticAuditAnalyzer {

    private final ObjectMapper objectMapper;

    public StaticAuditAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuditResponse analyze(URI uri, String html, boolean aiConfigured) {
        return analyze(uri, html, aiConfigured, null);
    }

    public AuditResponse analyze(URI uri, String html, boolean aiConfigured, String dynamicFailure) {
        return analyzeDocument(uri, html, aiConfigured, false, isCsrLikely(html, uri), null, dynamicFailure);
    }

    public AuditResponse analyzeDynamic(
            URI uri,
            String originalHtml,
            RenderedPage renderedPage,
            boolean aiConfigured
    ) {
        boolean csrLikely = isCsrLikely(originalHtml, uri);
        return analyzeDocument(uri, renderedPage.html(), aiConfigured, true, csrLikely, renderedPage, null);
    }

    private AuditResponse analyzeDocument(
            URI uri,
            String html,
            boolean aiConfigured,
            boolean dynamic,
            boolean csrLikely,
            RenderedPage renderedPage,
            String dynamicFailure
    ) {
        Document document = Jsoup.parse(html, uri.toString());
        Elements jsonLd = document.select("script[type=application/ld+json]");
        int validJsonLd = (int) jsonLd.stream().filter(this::isValidJson).count();
        int semanticElements = document.select("article, section, nav, main, header, footer, aside").size();
        int textLength = document.body() == null ? 0 : document.body().text().length();
        int missingAlt = document.select("img:not([alt])").size();
        int unlabeledControls = countUnlabelledControls(document);
        int headingSkips = countHeadingSkips(document);
        int emptyControls = document.select("button:empty, a[href]:empty").size();
        int h1Count = document.select("h1").size();

        int aeo = 100;
        if (jsonLd.isEmpty()) aeo -= 20;
        else if (validJsonLd < jsonLd.size()) aeo -= 10;
        if (h1Count == 0) aeo -= 15;
        else if (h1Count > 1) aeo -= 6;
        if (headingSkips > 0) aeo -= Math.min(15, headingSkips * 5);
        if (semanticElements < 3) aeo -= 12;
        if (textLength < 500) aeo -= 18;
        if (csrLikely && !dynamic) aeo -= 20;

        int a11y = 100;
        a11y -= Math.min(28, missingAlt * 4);
        a11y -= Math.min(30, unlabeledControls * 5);
        a11y -= Math.min(12, emptyControls * 4);
        if (!document.select("html[lang]").isEmpty()) {
            // Language is declared.
        } else {
            a11y -= 10;
        }
        a11y -= Math.min(10, headingSkips * 2);
        if (renderedPage != null) {
            a11y -= Math.min(24, renderedPage.insufficientContrast() * 4);
            a11y -= Math.min(20, renderedPage.smallTouchTargets() * 2);
        }

        aeo = Math.max(0, aeo);
        a11y = Math.max(0, a11y);
        int overall = Math.round((aeo + a11y) / 2.0f);

        List<AuditResponse.Issue> issues = new ArrayList<>();
        addSchemaIssue(issues, jsonLd.size(), validJsonLd);
        addCountIssue(issues, "alt", missingAlt, "Images are missing alt text",
                "Informative images cannot be understood by screen readers or multimodal crawlers.",
                "img:not([alt])", "Add concise alt text, or alt=\"\" when the image is decorative.");
        addCountIssue(issues, "labels", unlabeledControls, "Form controls need accessible labels",
                "Inputs without programmatic labels are ambiguous to assistive technology.",
                "input, select, textarea", "Associate a <label> using htmlFor/id or add aria-label/aria-labelledby.");
        addCountIssue(issues, "heading", headingSkips, "Heading hierarchy skips levels",
                "Skipped heading levels weaken the document outline used by crawlers and screen readers.",
                "h1, h2, h3, h4, h5, h6", "Keep heading levels sequential and use CSS for visual sizing.");
        if (renderedPage == null) {
            String description = dynamicFailure == null
                    ? "Color contrast and 44x44 touch targets need computed styles and element bounds from a browser."
                    : dynamicFailure;
            issues.add(new AuditResponse.Issue(
                    "dynamic-visual", "warning", "Visual checks require browser rendering",
                    description, 1, "rendered DOM",
                    "Run the backend with -Pdynamic-analysis after installing Playwright Chromium."));
        } else {
            addCountIssue(issues, "contrast", renderedPage.insufficientContrast(),
                    "Text has insufficient color contrast",
                    "Rendered text does not meet the WCAG contrast threshold for its computed size and weight.",
                    "visible text nodes", "Increase the foreground/background contrast to at least 4.5:1, or 3:1 for large text.");
            addCountIssue(issues, "touch-target", renderedPage.smallTouchTargets(),
                    "Interactive targets are smaller than 44x44",
                    "Small rendered controls are difficult to activate accurately on touch screens.",
                    "visible interactive elements", "Increase the clickable area to at least 44 by 44 CSS pixels.");
        }
        if (issues.stream().noneMatch(issue -> issue.severity().equals("critical"))) {
            issues.add(new AuditResponse.Issue(
                    "landmarks", "passed", "Core static checks passed",
                    "No critical schema, image alternative, or form-label issue was found in the fetched HTML.",
                    1, "html", "Keep the structure intact and confirm visual checks in the dynamic audit."));
        }

        String aiStatus = aiConfigured ? "ready" : "not-configured";
        String aiFeedback = aiConfigured
                ? "Gemini contextual assessment is pending."
                : "Set GEMINI_API_KEY to enable contextual answer-quality assessment.";

        return new AuditResponse(
                UUID.randomUUID(), uri.toString(), Instant.now(), 1,
                new AuditResponse.Scores(overall, aeo, a11y),
                new AuditResponse.CrawlSummary(dynamic ? "dynamic" : "static", csrLikely, textLength, semanticElements, jsonLd.size(), validJsonLd),
                new AuditResponse.AiAssessment(aiStatus, null, aiFeedback),
                List.copyOf(issues));
    }

    private boolean isCsrLikely(String html, URI uri) {
        Document document = Jsoup.parse(html, uri.toString());
        int textLength = document.body() == null ? 0 : document.body().text().length();
        return textLength < 200 && !document.select("#root, #app, [data-reactroot]").isEmpty();
    }

    private boolean isValidJson(Element script) {
        try {
            objectMapper.readTree(script.data());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private int countUnlabelledControls(Document document) {
        int count = 0;
        for (Element control : document.select("input:not([type=hidden]), select, textarea")) {
            boolean named = control.hasAttr("aria-label") || control.hasAttr("aria-labelledby");
            String id = control.id();
            boolean hasExplicitLabel = !id.isBlank() && !document.select("label[for=\"" + escapeCssAttribute(id) + "\"]").isEmpty();
            boolean hasWrappingLabel = control.parents().stream().anyMatch(parent -> parent.tagName().equals("label"));
            if (!named && !hasExplicitLabel && !hasWrappingLabel) count++;
        }
        return count;
    }

    private String escapeCssAttribute(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int countHeadingSkips(Document document) {
        int previousLevel = 0;
        int skips = 0;
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
            int currentLevel = heading.tagName().charAt(1) - '0';
            if (previousLevel > 0 && currentLevel > previousLevel + 1) skips++;
            previousLevel = currentLevel;
        }
        return skips;
    }

    private void addSchemaIssue(List<AuditResponse.Issue> issues, int total, int valid) {
        if (total == 0) {
            issues.add(new AuditResponse.Issue(
                    "schema", "critical", "JSON-LD structured data is missing",
                    "Answer engines have no explicit entity metadata for this page.",
                    1, "script[type=\"application/ld+json\"]",
                    "Add a valid JSON-LD block using the most specific Schema.org type for the content."));
        } else if (valid < total) {
            issues.add(new AuditResponse.Issue(
                    "schema-invalid", "critical", "Some JSON-LD blocks are invalid",
                    (total - valid) + " structured data block(s) could not be parsed as JSON.",
                    total - valid, "script[type=\"application/ld+json\"]",
                    "Fix JSON syntax and validate the result with a structured-data testing tool."));
        }
    }

    private void addCountIssue(List<AuditResponse.Issue> issues, String id, int count, String title,
                               String description, String selector, String fix) {
        if (count == 0) return;
        String severity = count >= 3 ? "critical" : "warning";
        issues.add(new AuditResponse.Issue(id, severity, title, description, count, selector, fix));
    }
}
