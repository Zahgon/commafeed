package com.commafeed.frontend.http;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A media type with its parameters, parsed the way the replaced runtime parsed one.
 *
 * The type and the subtype are compared without regard to case, and so are parameter names; a parameter value is kept as it was written,
 * because a {@code charset} a client asks for is written back into the answer exactly as it arrived.
 */
public final class MediaType {

	public static final String APPLICATION_JSON = "application/json";
	public static final String APPLICATION_XML = "application/xml";
	public static final String TEXT_PLAIN = "text/plain";
	public static final String TEXT_HTML = "text/html";
	public static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
	public static final String MULTIPART_FORM_DATA = "multipart/form-data";
	public static final String WILDCARD = "*/*";

	private final String type;
	private final String subtype;
	private final Map<String, String> parameters;

	private MediaType(String type, String subtype, Map<String, String> parameters) {
		this.type = type;
		this.subtype = subtype;
		this.parameters = Collections.unmodifiableMap(parameters);
	}

	/**
	 * Parses one media type, or answers null when the text is not one.
	 *
	 * A bare {@code *} is a wildcard in both halves, which is how the replaced runtime read it; a text with no subtype at all is not a
	 * media type.
	 */
	public static MediaType parse(String text) {
		if (text == null) {
			return null;
		}
		String rest = text.trim();
		if (rest.isEmpty()) {
			return null;
		}
		int semicolon = rest.indexOf(';');
		String full = (semicolon < 0 ? rest : rest.substring(0, semicolon)).trim();
		if ("*".equals(full)) {
			full = WILDCARD;
		}
		int slash = full.indexOf('/');
		if (slash <= 0 || slash == full.length() - 1) {
			return null;
		}
		String type = full.substring(0, slash).trim().toLowerCase(Locale.ROOT);
		String subtype = full.substring(slash + 1).trim().toLowerCase(Locale.ROOT);
		if (type.isEmpty() || subtype.isEmpty() || type.indexOf('/') >= 0 || subtype.indexOf('/') >= 0) {
			return null;
		}
		Map<String, String> parameters = new LinkedHashMap<>();
		if (semicolon >= 0) {
			for (String part : rest.substring(semicolon + 1).split(";")) {
				int equals = part.indexOf('=');
				if (equals <= 0) {
					continue;
				}
				String name = part.substring(0, equals).trim().toLowerCase(Locale.ROOT);
				String value = part.substring(equals + 1).trim();
				if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
					value = value.substring(1, value.length() - 1);
				}
				if (!name.isEmpty()) {
					parameters.putIfAbsent(name, value);
				}
			}
		}
		return new MediaType(type, subtype, parameters);
	}

	/**
	 * Reads one offer out of an Accept header.
	 *
	 * A bare word with no subtype reads as a type whose subtype is empty, which covers nothing: the replaced runtime read such an offer
	 * rather than rejecting it, and then found nothing it could write for it. A word carrying an equals sign before any slash is not an
	 * offer at all.
	 */
	public static MediaType offer(String text) {
		String rest = text.trim();
		int slash = rest.indexOf('/');
		int equals = rest.indexOf('=');
		if (equals >= 0 && (slash < 0 || equals < slash)) {
			return null;
		}
		if (slash < 0) {
			int semicolon = rest.indexOf(';');
			String word = (semicolon < 0 ? rest : rest.substring(0, semicolon)).trim();
			if ("*".equals(word)) {
				return parse(rest);
			}
			MediaType parsed = parse(word + "/x" + (semicolon < 0 ? "" : rest.substring(semicolon)));
			return parsed == null ? null : parsed.withEmptySubtype();
		}
		return parse(rest);
	}

	/** The same type with a subtype nothing matches, which is what a bare word read as. */
	MediaType withEmptySubtype() {
		return new MediaType(type, "", parameters);
	}

	public Map<String, String> getParameters() {
		return parameters;
	}

	public static MediaType of(String text) {
		MediaType parsed = parse(text);
		if (parsed == null) {
			throw new IllegalArgumentException("not a media type: " + text);
		}
		return parsed;
	}

	public String getType() {
		return type;
	}

	public String getSubtype() {
		return subtype;
	}

	public String getParameter(String name) {
		return parameters.get(name.toLowerCase(Locale.ROOT));
	}

	public boolean isWildcardType() {
		return "*".equals(type);
	}

	public boolean isWildcardSubtype() {
		return "*".equals(subtype);
	}

	/** Whether this type, read as an offer, covers the given concrete type. */
	public boolean covers(MediaType concrete) {
		if (!isWildcardType() && !type.equals(concrete.type)) {
			return false;
		}
		return isWildcardSubtype() || subtype.equals(concrete.subtype);
	}

	/** The type without its parameters, as it is written into an answer. */
	public String bare() {
		return type + "/" + subtype;
	}

	/** This type with the given parameter added when it is not already carried. */
	public MediaType withParameter(String name, String value) {
		Map<String, String> next = new LinkedHashMap<>(parameters);
		next.put(name.toLowerCase(Locale.ROOT), value);
		return new MediaType(type, subtype, next);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder(bare());
		for (Map.Entry<String, String> parameter : parameters.entrySet()) {
			text.append(';').append(parameter.getKey()).append('=').append(parameter.getValue());
		}
		return text.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof MediaType)) {
			return false;
		}
		MediaType that = (MediaType) other;
		return type.equals(that.type) && subtype.equals(that.subtype) && parameters.equals(that.parameters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, subtype, parameters);
	}

}
