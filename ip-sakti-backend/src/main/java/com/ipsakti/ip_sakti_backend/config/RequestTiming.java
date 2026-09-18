package com.ipsakti.ip_sakti_backend.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Per-request, non-sensitive timing ledger used for Server-Timing and the developer watch. */
public final class RequestTiming {
    private static final ThreadLocal<RequestTiming> CURRENT = new ThreadLocal<>();
    private final long startedNanos = System.nanoTime();
    private final Map<String, Double> stages = new LinkedHashMap<>();
    private String provider = "none";
    private String routeName = "UNKNOWN";
    private int chunks;

    public static void start() { CURRENT.set(new RequestTiming()); }
    public static void clear() { CURRENT.remove(); }
    public static RequestTiming current() { return CURRENT.get(); }
    public static void record(String name, long startedNanos) {
        RequestTiming timing = CURRENT.get();
        if (timing != null) timing.stages.put(name, elapsed(startedNanos));
    }
    public static void set(String name, double durationMs) {
        RequestTiming timing = CURRENT.get();
        if (timing != null) timing.stages.put(name, Math.max(0, durationMs));
    }
    public static void provider(String value) { if (CURRENT.get() != null) CURRENT.get().provider = value == null ? "unknown" : value; }
    public static void chunks(int value) { if (CURRENT.get() != null) CURRENT.get().chunks = Math.max(0, value); }
    public static void routeName(String value) { if (CURRENT.get() != null) CURRENT.get().routeName = value == null ? "UNKNOWN" : value; }
    public static long now() { return System.nanoTime(); }
    public static double elapsed(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000.0; }

    public String serverTiming() {
        Map<String, Double> values = new LinkedHashMap<>(stages);
        values.put("total", elapsed(startedNanos));
        return values.entrySet().stream()
                .map(e -> e.getKey() + ";dur=" + String.format(Locale.ROOT, "%.1f", e.getValue()))
                .reduce((a, b) -> a + ", " + b).orElse("total;dur=0");
    }
    public Map<String, Double> stages() { return Map.copyOf(stages); }
    public String provider() { return provider; }
    public String routeName() { return routeName; }
    public int chunks() { return chunks; }
    public double totalMs() { return elapsed(startedNanos); }
}
