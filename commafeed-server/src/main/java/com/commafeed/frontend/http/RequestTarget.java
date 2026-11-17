package com.commafeed.frontend.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The target of a request, read the way the replaced stack read it.
 *
 * Three things here are behaviour rather than convenience, because the replaced stack did them and a client can see each of them:
 *
 * <ul>
 * <li>the path is kept exactly as it arrived - a percent-encoded character is <em>not</em> decoded before a route is matched against it, so
 * {@code /%73erver} does not reach the resource that {@code /server} reaches, and a dot segment is an ordinary segment rather than a step
 * up;</li>
 * <li>a matrix parameter is cut off each segment before matching, so {@code /get;x=1} reaches what {@code /get} reaches;</li>
 * <li>a query parameter is decoded with {@code +} standing for a space, and a parameter a client repeats is read as its first value.</li>
 * </ul>
 */
public final class RequestTarget {

	private final String rawPath;
	private final String rawQuery;
	private final List<String> segments;
	private final Map<String, List<String>> queryParameters;

	private RequestTarget(String rawPath, String rawQuery, List<String> segments, Map<String, List<String>> queryParameters) {
		this.rawPath = rawPath;
		this.rawQuery = rawQuery;
		this.segments = Collections.unmodifiableList(segments);
		this.queryParameters = Collections.unmodifiableMap(queryParameters);
	}

	public static RequestTarget of(String rawPath, String rawQuery) {
		String path = rawPath == null || rawPath.isEmpty() ? "/" : rawPath;
		List<String> segments = new ArrayList<>();
		for (String segment : path.split("/", -1)) {
			int semicolon = segment.indexOf(';');
			segments.add(semicolon < 0 ? segment : segment.substring(0, semicolon));
		}
		// the split keeps a leading empty segment for the leading slash
		if (!segments.isEmpty()) {
			segments.remove(0);
		}
		return new RequestTarget(path, rawQuery, segments, readQuery(rawQuery));
	}

	private static Map<String, List<String>> readQuery(String rawQuery) {
		Map<String, List<String>> parameters = new LinkedHashMap<>();
		if (rawQuery == null || rawQuery.isEmpty()) {
			return parameters;
		}
		for (String pair : rawQuery.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int equals = pair.indexOf('=');
			String name = decode(equals < 0 ? pair : pair.substring(0, equals), true);
			String value = equals < 0 ? "" : decode(pair.substring(equals + 1), true);
			parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
		}
		return parameters;
	}

	/**
	 * Decodes percent escapes, and in a query also {@code +} as a space.
	 *
	 * A percent escape that is not two hexadecimal digits is left as it was written, which is what the replaced stack did with one.
	 */
	public static String decode(String text, boolean plusIsSpace) {
		if (text.indexOf('%') < 0 && !(plusIsSpace && text.indexOf('+') >= 0)) {
			return text;
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(text.length());
		int at = 0;
		while (at < text.length()) {
			char character = text.charAt(at);
			if (character == '%' && at + 2 < text.length() && isHex(text.charAt(at + 1)) && isHex(text.charAt(at + 2))) {
				bytes.write(Integer.parseInt(text.substring(at + 1, at + 3), 16));
				at += 3;
			} else if (plusIsSpace && character == '+') {
				bytes.write(' ');
				at++;
			} else {
				byte[] encoded = String.valueOf(character).getBytes(StandardCharsets.UTF_8);
				bytes.write(encoded, 0, encoded.length);
				at++;
			}
		}
		return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
	}

	private static boolean isHex(char character) {
		return character >= '0' && character <= '9' || character >= 'a' && character <= 'f' || character >= 'A' && character <= 'F';
	}

	public String getRawPath() {
		return rawPath;
	}

	public String getRawQuery() {
		return rawQuery;
	}

	/** The path without its query, with every matrix parameter cut off. */
	public String getMatchedPath() {
		return "/" + String.join("/", segments);
	}

	/** The segments of the path, still encoded, with every matrix parameter cut off. */
	public List<String> getSegments() {
		return segments;
	}

	/** The first value a query parameter carries, or null when it carries none. */
	public String getQueryParameter(String name) {
		List<String> values = queryParameters.get(name);
		return values == null || values.isEmpty() ? null : values.get(0);
	}

	public boolean hasQueryParameter(String name) {
		return queryParameters.containsKey(name);
	}

	public Map<String, List<String>> getQueryParameters() {
		return queryParameters;
	}

	/** The target below the given prefix, or null when the target is not under it. */
	public RequestTarget under(String prefix) {
		List<String> prefixSegments = RequestTarget.of(prefix, null).getSegments();
		if (segments.size() < prefixSegments.size()) {
			return null;
		}
		for (int at = 0; at < prefixSegments.size(); at++) {
			if (!prefixSegments.get(at).equals(segments.get(at))) {
				return null;
			}
		}
		List<String> rest = new ArrayList<>(segments.subList(prefixSegments.size(), segments.size()));
		return new RequestTarget(rawPath, rawQuery, rest, new LinkedHashMap<>(queryParameters));
	}

}
