package com.aeolens.api.audit;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

@Service
public class AuditService {

    private static final String USER_AGENT = "AeoLensBot/0.1 (+https://github.com/aeolens)";
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final TargetUrlValidator targetUrlValidator;
    private final StaticAuditAnalyzer staticAuditAnalyzer;
    private final DynamicPageAnalyzer dynamicPageAnalyzer;
    private final GeminiAssessmentService geminiAssessmentService;
    private final WebClient webClient;
    private final Duration responseTimeout;

    public AuditService(
            TargetUrlValidator targetUrlValidator,
            StaticAuditAnalyzer staticAuditAnalyzer,
            ObjectProvider<DynamicPageAnalyzer> dynamicPageAnalyzerProvider,
            GeminiAssessmentService geminiAssessmentService,
            @Value("${aeolens.crawler.connect-timeout:3s}") Duration connectTimeout,
            @Value("${aeolens.crawler.response-timeout:10s}") Duration responseTimeout,
            @Value("${aeolens.crawler.max-content-length:4000000}") int maxContentLength
    ) {
        this.targetUrlValidator = targetUrlValidator;
        this.staticAuditAnalyzer = staticAuditAnalyzer;
        this.dynamicPageAnalyzer = dynamicPageAnalyzerProvider.getIfAvailable();
        this.geminiAssessmentService = geminiAssessmentService;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(responseTimeout);
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxContentLength))
                .build();
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .build();
        this.responseTimeout = responseTimeout;
    }

    public Mono<AuditResponse> analyze(String rawUrl) {
        return targetUrlValidator.validate(rawUrl)
                .flatMap(this::fetchHtml)
                .flatMap(page -> Mono.fromCallable(() -> analyzePage(page))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(page -> geminiAssessmentService.assess(page.response(), page.analyzedHtml()));
    }

    private AnalyzedPage analyzePage(FetchedPage page) {
        boolean aiConfigured = geminiAssessmentService.isConfigured();
        AuditResponse staticResponse = staticAuditAnalyzer.analyze(page.uri(), page.html(), aiConfigured);
        if (!staticResponse.crawl().csrLikely() || dynamicPageAnalyzer == null) {
            return new AnalyzedPage(staticResponse, page.html());
        }

        try {
            RenderedPage renderedPage = dynamicPageAnalyzer.analyze(page.uri());
            AuditResponse response = staticAuditAnalyzer.analyzeDynamic(
                    page.uri(), page.html(), renderedPage, aiConfigured);
            return new AnalyzedPage(response, renderedPage.html());
        } catch (RuntimeException exception) {
            logger.warn("Dynamic analysis failed for {}; returning the static audit", page.uri(), exception);
            AuditResponse response = staticAuditAnalyzer.analyze(
                    page.uri(), page.html(), aiConfigured,
                    "Browser rendering was attempted but could not be completed. "
                            + "Confirm that Playwright Chromium is installed and reachable.");
            return new AnalyzedPage(response, page.html());
        }
    }

    private Mono<FetchedPage> fetchHtml(URI uri) {
        return webClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.createException().flatMap(Mono::error))
                .bodyToMono(String.class)
                .timeout(responseTimeout)
                .switchIfEmpty(Mono.error(new IllegalStateException("The target returned an empty response")))
                .map(html -> new FetchedPage(uri, html));
    }

    private record FetchedPage(URI uri, String html) {
    }

    private record AnalyzedPage(AuditResponse response, String analyzedHtml) {
    }
}
