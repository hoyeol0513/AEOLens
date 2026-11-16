package com.aeolens.api.audit;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
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
    private static final int MAX_REDIRECTS = 5;
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final TargetUrlValidator targetUrlValidator;
    private final StaticAuditAnalyzer staticAuditAnalyzer;
    private final DynamicPageAnalyzer dynamicPageAnalyzer;
    private final GeminiAssessmentService geminiAssessmentService;
    private final WebClient webClient;
    private final Duration responseTimeout;
    private final int maxContentLength;

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
        this.maxContentLength = maxContentLength;
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
        if (dynamicPageAnalyzer == null) {
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
                    "Browser-based visual analysis was attempted but could not be completed. "
                            + "Confirm that Playwright Chromium is installed and reachable.");
            return new AnalyzedPage(response, page.html());
        }
    }

    private Mono<FetchedPage> fetchHtml(URI uri) {
        return fetchHop(uri, 0)
                .timeout(responseTimeout)
                .onErrorMap(AuditService::exceedsBufferLimit, error -> new TargetFetchException(
                        "The target page is larger than the " + (maxContentLength / 1_000_000)
                                + " MB audit limit", error));
    }

    /**
     * Fetches one URL and follows redirects manually so that every hop is re-validated
     * against the public-host policy before any request is sent to it.
     */
    private Mono<FetchedPage> fetchHop(URI uri, int redirectCount) {
        return webClient.get()
                .uri(uri)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    if (status.is3xxRedirection()) {
                        String location = response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
                        return response.releaseBody().thenReturn(FetchStep.redirect(location));
                    }
                    if (status.isError()) {
                        return response.createException().flatMap(error -> Mono.<FetchStep>error(error));
                    }
                    return response.bodyToMono(String.class)
                            .switchIfEmpty(Mono.error(
                                    new TargetFetchException("The target returned an empty response")))
                            .map(html -> FetchStep.page(new FetchedPage(uri, html)));
                })
                .flatMap(step -> step.page() != null
                        ? Mono.just(step.page())
                        : followRedirect(uri, step.location(), redirectCount));
    }

    private Mono<FetchedPage> followRedirect(URI from, String location, int redirectCount) {
        if (redirectCount >= MAX_REDIRECTS) {
            return Mono.error(new TargetFetchException(
                    "The target redirected more than " + MAX_REDIRECTS + " times"));
        }
        if (location == null || location.isBlank()) {
            return Mono.error(new TargetFetchException("The target sent a redirect without a Location header"));
        }

        URI next;
        try {
            // URI.resolve mishandles relative references against an empty path ("http://host").
            URI base = from.getRawPath() == null || from.getRawPath().isEmpty() ? from.resolve("/") : from;
            next = base.resolve(location.trim());
        } catch (IllegalArgumentException exception) {
            return Mono.error(new TargetFetchException("The target sent an invalid redirect location", exception));
        }

        return targetUrlValidator.validate(next.toString())
                .onErrorMap(InvalidAuditTargetException.class, error -> new InvalidAuditTargetException(
                        "The target redirected to a disallowed location: " + error.getMessage(), error))
                .flatMap(validated -> fetchHop(validated, redirectCount + 1));
    }

    private static boolean exceedsBufferLimit(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DataBufferLimitException) {
                return true;
            }
        }
        return false;
    }

    private record FetchStep(FetchedPage page, String location) {
        static FetchStep page(FetchedPage page) {
            return new FetchStep(page, null);
        }

        static FetchStep redirect(String location) {
            return new FetchStep(null, location);
        }
    }

    private record FetchedPage(URI uri, String html) {
    }

    private record AnalyzedPage(AuditResponse response, String analyzedHtml) {
    }
}
