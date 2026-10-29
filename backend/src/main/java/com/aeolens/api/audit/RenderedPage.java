package com.aeolens.api.audit;

import java.net.URI;

public record RenderedPage(
        URI finalUri,
        String html,
        int insufficientContrast,
        int smallTouchTargets
) {
}
