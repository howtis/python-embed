package com.example.pythonembed;

import java.util.List;

/**
 * Health information collected from a Python process.
 *
 * @param memoryRssKb RSS memory usage in KB (from {@code resource.getrusage(RUSAGE_SELF).ru_maxrss})
 * @param refCount    number of active object references held by Java
 * @param gcEnabled   whether Python garbage collection is enabled
 * @param gcCounts    GC collection counts: {@code [gen0, gen1, gen2]}
 */
public record HealthInfo(
    long memoryRssKb,
    int refCount,
    boolean gcEnabled,
    List<Integer> gcCounts
) {}
