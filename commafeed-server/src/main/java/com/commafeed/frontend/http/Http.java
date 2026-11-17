package com.commafeed.frontend.http;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The small pieces of HTTP spelling the serving layer writes. */
public final class Http {

	/** The date format a header carries, which is the one the replaced container wrote. */
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
			.withZone(ZoneOffset.UTC);

	private Http() {
	}

	public static String formatDate(Instant when) {
		return DATE.format(when);
	}

	/** Parses a date a header carries, or answers null when it carries none that reads. */
	public static Instant parseDate(String text) {
		if (text == null) {
			return null;
		}
		try {
			return Instant.from(DATE.parse(text));
		} catch (RuntimeException notADate) {
			return null;
		}
	}

}
