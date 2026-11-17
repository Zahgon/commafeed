package com.commafeed.frontend.http;

import java.util.List;

/**
 * Marks the answers that are not to be cached, on the same targets as the filters the replaced wiring registered.
 *
 * There were four registrations and they are not the same as "everything the application answers": the index document is marked under the
 * target it is served at but not under its own file name, both OpenAPI documents are marked, and everything under the API prefix is marked
 * <em>except</em> the target the favicons are served from, which the replaced wiring let through so that a browser could cache an icon.
 */
public final class CacheBusting {

	/** What the replaced filter wrote, spelled as it wrote it. */
	public static final String NO_CACHE = "must-revalidate,no-cache,no-store";

	/** The targets the replaced wiring registered a filter on, one by one. */
	private static final List<String> EXACT = List.of("/", "/openapi.json", "/openapi.yaml");

	/** The prefix the fourth registration covered. */
	private static final String API_PREFIX = "/rest";

	/** The one target under that prefix the fourth registration let through. */
	private static final String FAVICONS = "/feed/favicon";

	private CacheBusting() {
	}

	/** Whether an answer on this target is marked as not to be cached. */
	public static boolean marks(String path) {
		if (EXACT.contains(path)) {
			return true;
		}
		if (!path.equals(API_PREFIX) && !path.startsWith(API_PREFIX + "/")) {
			return false;
		}
		return !path.contains(FAVICONS);
	}

	/** Marks the answer when the target it was asked on is one of the registered ones. */
	public static void apply(String path, Reply reply) {
		if (marks(path)) {
			reply.setHeader("Cache-Control", NO_CACHE);
		}
	}

}
