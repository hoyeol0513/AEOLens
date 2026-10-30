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

    URI validateBlocking(String rawUrl) throws Exception {
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
                .anyMatch(this::isNonPublicAddress);
        if (privateAddress) {
            throw new IllegalArgumentException("Private or local network targets are not allowed");
        }
        return uri;
    }

    private boolean isNonPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean carrierGradeNat = first == 100 && second >= 64 && second <= 127;
            boolean benchmarkingRange = first == 198 && (second == 18 || second == 19);
            boolean reservedRange = first >= 240;
            return carrierGradeNat || benchmarkingRange || reservedRange;
        }

        boolean uniqueLocalIpv6 = (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
        return uniqueLocalIpv6;
    }
}
