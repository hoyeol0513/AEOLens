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
    private final WebClient webClient;
    private final Duration responseTimeout;
    private final boolean aiConfigured;

    public AuditService(
            TargetUrlValidator targetUrlValidator,
            StaticAuditAnalyzer staticAuditAnalyzer,
            ObjectProvider<DynamicPageAnalyzer> dynamicPageAnalyzerProvider,
            @Value("${aeolens.crawler.connect-timeout:3s}") Duration connectTimeout,
            @Value("${aeolens.crawler.response-timeout:10s}") Duration responseTimeout,
            @Value("${aeolens.crawler.max-content-length:4000000}") int maxContentLength,
            @Value("${aeolens.gemini.api-key:}") String geminiApiKey
    ) {
        this.targetUrlValidator = targetUrlValidator;
        this.staticAuditAnalyzer = staticAuditAnalyzer;
        this.dynamicPageAnalyzer = dynamicPageAnalyzerProvider.getIfAvailable();
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
        this.aiConfigured = !geminiApiKey.isBlank();
    }

    public Mono<AuditResponse> analyze(String rawUrl) {
        return targetUrlValidator.validate(rawUrl)
                .flatMap(this::fetchHtml)
                .flatMap(page -> Mono.fromCallable(() -> analyzePage(page))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private AuditResponse analyzePage(FetchedPage page) {
        AuditResponse staticResponse = staticAuditAnalyzer.analyze(page.uri(), page.html(), aiConfigured);
        if (!staticResponse.crawl().csrLikely() || dynamicPageAnalyzer == null) {
            return staticResponse;
        }

        try {
            RenderedPage renderedPage = dynamicPageAnalyzer.analyze(page.uri());
            return staticAuditAnalyzer.analyzeDynamic(
                    page.uri(), page.html(), renderedPage, aiConfigured);
        } catch (RuntimeException exception) {
            logger.warn("Dynamic analysis failed for {}; returning the static audit", page.uri(), exception);
            return staticAuditAnalyzer.analyze(
                    page.uri(), page.html(), aiConfigured,
                    "Browser rendering was attempted but could not be completed. "
                            + "Confirm that Playwright Chromium is installed and reachable.");
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
}
