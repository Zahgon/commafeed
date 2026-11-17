package com.commafeed.config;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * An amount of time written the way the configuration writes one: a count and a unit, as in {@code 15m} or {@code 30 days}.
 *
 * The replaced framework offered this type and the configuration file uses its spelling, so the spelling is kept rather than changed along
 * with the framework.
 */
public final class Duration implements Comparable<Duration> {

	private static final Pattern SPELLING = Pattern.compile("(\\d+)\\s*(\\S+)");

	private static final Map<String, TimeUnit> UNITS = Map.ofEntries(Map.entry("ns", TimeUnit.NANOSECONDS),
			Map.entry("nanosecond", TimeUnit.NANOSECONDS), Map.entry("nanoseconds", TimeUnit.NANOSECONDS),
			Map.entry("us", TimeUnit.MICROSECONDS), Map.entry("microsecond", TimeUnit.MICROSECONDS),
			Map.entry("microseconds", TimeUnit.MICROSECONDS), Map.entry("ms", TimeUnit.MILLISECONDS),
			Map.entry("millisecond", TimeUnit.MILLISECONDS), Map.entry("milliseconds", TimeUnit.MILLISECONDS),
			Map.entry("s", TimeUnit.SECONDS), Map.entry("second", TimeUnit.SECONDS), Map.entry("seconds", TimeUnit.SECONDS),
			Map.entry("m", TimeUnit.MINUTES), Map.entry("minute", TimeUnit.MINUTES), Map.entry("minutes", TimeUnit.MINUTES),
			Map.entry("h", TimeUnit.HOURS), Map.entry("hour", TimeUnit.HOURS), Map.entry("hours", TimeUnit.HOURS),
			Map.entry("d", TimeUnit.DAYS), Map.entry("day", TimeUnit.DAYS), Map.entry("days", TimeUnit.DAYS));

	private final long count;
	private final TimeUnit unit;

	private Duration(long count, TimeUnit unit) {
		this.count = count;
		this.unit = unit;
	}

	public static Duration seconds(long count) {
		return new Duration(count, TimeUnit.SECONDS);
	}

	public static Duration minutes(long count) {
		return new Duration(count, TimeUnit.MINUTES);
	}

	public static Duration days(long count) {
		return new Duration(count, TimeUnit.DAYS);
	}

	@JsonCreator
	public static Duration parse(String spelling) {
		Matcher matcher = SPELLING.matcher(spelling);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("not a duration: " + spelling);
		}
		TimeUnit unit = UNITS.get(matcher.group(2).toLowerCase(Locale.ROOT));
		if (unit == null) {
			throw new IllegalArgumentException("not a duration unit: " + matcher.group(2));
		}
		return new Duration(Long.parseLong(matcher.group(1)), unit);
	}

	public long getCount() {
		return count;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	public long toMilliseconds() {
		return TimeUnit.MILLISECONDS.convert(count, unit);
	}

	public long toSeconds() {
		return TimeUnit.SECONDS.convert(count, unit);
	}

	@Override
	public int compareTo(Duration other) {
		return Long.compare(toMilliseconds(), other.toMilliseconds());
	}

	@JsonValue
	@Override
	public String toString() {
		String name = unit.name().toLowerCase(Locale.ROOT);
		return count + " " + (count == 1 ? name.substring(0, name.length() - 1) : name);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Duration && toMilliseconds() == ((Duration) other).toMilliseconds();
	}

	@Override
	public int hashCode() {
		return Long.hashCode(toMilliseconds());
	}

}
