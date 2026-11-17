package com.commafeed.frontend.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The template a resource declares its target as, compiled into one pattern over the whole path.
 *
 * It is one pattern rather than a comparison segment by segment because a template may carry a pattern of its own in braces, and such a
 * pattern may cover a slash: the Fever-compatible surface declares two optional tails that way, and a target that carries one of them
 * reaches the same method as the target without it.
 *
 * A name in braces with no pattern of its own claims one segment that is not empty, and the whole template claims one trailing slash as
 * well, which is why {@code /server/get/} reaches what {@code /server/get} reaches.
 */
public final class PathTemplate {

	private final String declared;
	private final Pattern pattern;
	private final List<String> names;

	private PathTemplate(String declared, Pattern pattern, List<String> names) {
		this.declared = declared;
		this.pattern = pattern;
		this.names = names;
	}

	public static PathTemplate of(String declared) {
		StringBuilder regex = new StringBuilder();
		List<String> names = new ArrayList<>();
		int at = 0;
		while (at < declared.length()) {
			char character = declared.charAt(at);
			if (character != '{') {
				regex.append(Pattern.quote(String.valueOf(character)));
				at++;
				continue;
			}
			int close = closingBrace(declared, at);
			String inside = declared.substring(at + 1, close);
			int colon = inside.indexOf(':');
			names.add((colon < 0 ? inside : inside.substring(0, colon)).trim());
			regex.append('(').append(colon < 0 ? "[^/]+?" : inside.substring(colon + 1).trim()).append(')');
			at = close + 1;
		}
		return new PathTemplate(declared, Pattern.compile(regex + "(/)?"), names);
	}

	/** The brace that closes the one at {@code open}, counting the braces in between. */
	private static int closingBrace(String declared, int open) {
		int depth = 0;
		for (int at = open; at < declared.length(); at++) {
			if (declared.charAt(at) == '{') {
				depth++;
			} else if (declared.charAt(at) == '}' && --depth == 0) {
				return at;
			}
		}
		throw new IllegalArgumentException("unclosed brace in " + declared);
	}

	/**
	 * Whether this template claims the whole of the given path, filling in what it captures.
	 *
	 * What is captured is the path as it arrived: a percent escape inside it is decoded afterwards, when the value is converted to what the
	 * resource method takes.
	 */
	public boolean claims(String path, Map<String, String> captured) {
		Matcher matcher = pattern.matcher(path);
		if (!matcher.matches()) {
			return false;
		}
		for (int at = 0; at < names.size(); at++) {
			captured.put(names.get(at), matcher.group(at + 1));
		}
		return true;
	}

	@Override
	public String toString() {
		return declared;
	}

}
