package com.aeolens.api.audit;

import java.net.URI;

/**
 * Optional browser-backed analyzer. The implementation is compiled only when
 * the Maven dynamic-analysis profile is enabled.
 */
public interface DynamicPageAnalyzer {

    RenderedPage analyze(URI uri);
}
