package com.aeolens.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TargetUrlValidatorTests {

    private final TargetUrlValidator validator = new TargetUrlValidator();

    @Test
    void acceptsPublicHttpAddress() throws Exception {
        URI uri = validator.validateBlocking("https://8.8.8.8/example");

        assertThat(uri).isEqualTo(URI.create("https://8.8.8.8/example"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://8.8.8.8/file",
            "https://user:password@8.8.8.8/",
            "http://localhost/",
            "http://127.0.0.1/",
            "http://10.0.0.1/",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1/",
            "http://198.18.0.1/",
            "http://[::1]/",
            "http://[fc00::1]/"
    })
    void rejectsUnsupportedOrNonPublicTargets(String rawUrl) {
        assertThatThrownBy(() -> validator.validateBlocking(rawUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
