package com.aeolens.api.audit;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class TargetUrlValidator {

    public Mono<URI> validate(String rawUrl) {
        return Mono.fromCallable(() -> validateBlocking(rawUrl))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private URI validateBlocking(String rawUrl) throws Exception {
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http:// and https:// URLs are supported");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URLs containing credentials are not supported");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank() || host.equalsIgnoreCase("localhost")) {
            throw new IllegalArgumentException("A public hostname is required");
        }

        boolean privateAddress = Arrays.stream(InetAddress.getAllByName(host))
                .anyMatch(address -> address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress());
        if (privateAddress) {
            throw new IllegalArgumentException("Private or local network targets are not allowed");
        }
        return uri;
    }
}
