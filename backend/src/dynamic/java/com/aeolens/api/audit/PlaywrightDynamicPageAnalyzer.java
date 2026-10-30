package com.aeolens.api.audit;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

@Component
public class PlaywrightDynamicPageAnalyzer implements DynamicPageAnalyzer {

    private static final String USER_AGENT = "AeoLensBot/0.1 (+https://github.com/aeolens)";

    private static final String VISUAL_METRICS_SCRIPT = """
            () => {
              const parseColor = (value) => {
                const match = value && value.match(/rgba?\\(([^)]+)\\)/);
                if (!match) return null;
                const parts = match[1].split(/[ ,/]+/).filter(Boolean).map(Number);
                return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 };
              };
              const luminance = (color) => {
                const channels = [color.r, color.g, color.b].map((channel) => {
                  const value = channel / 255;
                  return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
                });
                return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
              };
              const contrast = (first, second) => {
                const high = Math.max(luminance(first), luminance(second));
                const low = Math.min(luminance(first), luminance(second));
                return (high + 0.05) / (low + 0.05);
              };
              const isVisible = (element, style = getComputedStyle(element)) => {
                const rect = element.getBoundingClientRect();
                return style.display !== 'none' && style.visibility !== 'hidden'
                  && Number(style.opacity) > 0 && rect.width > 0 && rect.height > 0;
              };
              const backgroundFor = (element) => {
                let current = element;
                while (current) {
                  const style = getComputedStyle(current);
                  if (style.backgroundImage !== 'none') return null;
                  const color = parseColor(style.backgroundColor);
                  if (color && color.a >= 0.95) return color;
                  current = current.parentElement;
                }
                return { r: 255, g: 255, b: 255, a: 1 };
              };

              let contrastFailures = 0;
              for (const element of document.body?.querySelectorAll('*') ?? []) {
                const hasDirectText = [...element.childNodes]
                  .some((node) => node.nodeType === Node.TEXT_NODE && node.textContent.trim().length > 0);
                if (!hasDirectText) continue;
                const style = getComputedStyle(element);
                if (!isVisible(element, style)) continue;
                const foreground = parseColor(style.color);
                const background = backgroundFor(element);
                if (!foreground || !background) continue;
                const fontSize = Number.parseFloat(style.fontSize);
                const fontWeight = Number.parseInt(style.fontWeight, 10) || 400;
                const largeText = fontSize >= 24 || (fontSize >= 18.66 && fontWeight >= 700);
                if (contrast(foreground, background) < (largeText ? 3 : 4.5)) contrastFailures++;
              }

              let smallTouchTargets = 0;
              const interactive = 'a[href], button, input:not([type=hidden]), select, textarea, [role=button], [role=link]';
              for (const element of document.querySelectorAll(interactive)) {
                const style = getComputedStyle(element);
                if (!isVisible(element, style) || element.matches(':disabled')) continue;
                if (element.tagName === 'A' && style.display === 'inline') continue;
                const rect = element.getBoundingClientRect();
                if (rect.width < 44 || rect.height < 44) smallTouchTargets++;
              }
              return { contrastFailures, smallTouchTargets };
            }
            """;

    private final TargetUrlValidator targetUrlValidator;
    private final Duration navigationTimeout;
    private final Duration networkIdleTimeout;

    public PlaywrightDynamicPageAnalyzer(
            TargetUrlValidator targetUrlValidator,
            @Value("${aeolens.dynamic.navigation-timeout:15s}") Duration navigationTimeout,
            @Value("${aeolens.dynamic.network-idle-timeout:5s}") Duration networkIdleTimeout
    ) {
        this.targetUrlValidator = targetUrlValidator;
        this.navigationTimeout = navigationTimeout;
        this.networkIdleTimeout = networkIdleTimeout;
    }

    @Override
    public RenderedPage analyze(URI uri) {
        Map<String, Boolean> allowedHosts = new ConcurrentHashMap<>();
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(USER_AGENT)
                        .setViewportSize(1440, 900));
                try {
                    context.route("**/*", route -> {
                        if (isAllowed(route.request().url(), allowedHosts)) {
                            route.resume();
                        } else {
                            route.abort();
                        }
                    });

                    Page page = context.newPage();
                    page.navigate(uri.toString(), new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(navigationTimeout.toMillis()));
                    try {
                        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions()
                                .setTimeout(networkIdleTimeout.toMillis()));
                    } catch (PlaywrightException ignored) {
                        // Long-lived connections are common in SPAs; the DOM is still usable.
                    }

                    URI finalUri = targetUrlValidator.validateBlocking(page.url());
                    Object evaluated = page.evaluate(VISUAL_METRICS_SCRIPT);
                    if (!(evaluated instanceof Map<?, ?> metrics)) {
                        throw new IllegalStateException("Browser returned invalid visual metrics");
                    }
                    return new RenderedPage(
                            finalUri,
                            page.content(),
                            readMetric(metrics, "contrastFailures"),
                            readMetric(metrics, "smallTouchTargets"));
                } finally {
                    context.close();
                }
            } finally {
                browser.close();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Dynamic page analysis failed", exception);
        }
    }

    private boolean isAllowed(String rawUrl, Map<String, Boolean> allowedHosts) {
        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if ("data".equalsIgnoreCase(scheme) || "blob".equalsIgnoreCase(scheme)) {
                return true;
            }
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                return false;
            }
            String key = scheme + "://" + uri.getHost() + ":" + uri.getPort();
            return allowedHosts.computeIfAbsent(key, ignored -> {
                try {
                    targetUrlValidator.validateBlocking(rawUrl);
                    return true;
                } catch (Exception exception) {
                    return false;
                }
            });
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private int readMetric(Map<?, ?> metrics, String name) {
        Object value = metrics.get(name);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        throw new IllegalStateException("Browser metric is missing: " + name);
    }
}
