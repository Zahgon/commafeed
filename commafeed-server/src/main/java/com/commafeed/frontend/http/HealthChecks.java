package com.commafeed.frontend.http;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The checks the second connector answers with, and the document it writes about them.
 *
 * The replaced framework registered two of them - one that looks for deadlocked threads and one that asks the database whether it is there
 * - and wrote each as whether it is healthy, how long it took and when it ran. The document's shape is what a client reads, so it is kept.
 */
public final class HealthChecks {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	private final Map<String, BooleanSupplier> checks = new LinkedHashMap<>();

	public void register(String name, BooleanSupplier check) {
		checks.put(name, check);
	}

	/** What every check says, in the order they were registered. */
	public Map<String, Object> run() {
		Map<String, Object> results = new LinkedHashMap<>();
		checks.forEach((name, check) -> {
			long started = System.currentTimeMillis();
			boolean healthy;
			try {
				healthy = check.getAsBoolean();
			} catch (RuntimeException failed) {
				healthy = false;
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("healthy", healthy);
			result.put("duration", System.currentTimeMillis() - started);
			result.put("timestamp", WHEN.format(Instant.now()));
			results.put(name, result);
		});
		return results;
	}

}
