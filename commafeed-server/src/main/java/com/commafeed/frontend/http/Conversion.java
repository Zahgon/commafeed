package com.commafeed.frontend.http;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Turns the text of a path or query parameter into what the resource method takes.
 *
 * Two things here are behaviour a client can see, and both come from the replaced framework rather than from the language:
 *
 * <ul>
 * <li>a value that will not convert to a number leaves the request unmatched, so the server answers that it has no such target rather than
 * that the request was bad;</li>
 * <li>a value that names no constant of an enum is answered as a bad request whose message names the constants, and the name in that
 * message is the kind of parameter followed by its name.</li>
 * </ul>
 *
 * An empty value is a third: converting it is not an error but no value at all, so the declared default takes over, and a parameter with no
 * default is left null.
 */
public final class Conversion {

	private Conversion() {
	}

	/**
	 * The value for one parameter.
	 *
	 * @param text
	 *            the value the request carried, or null when it carried none
	 * @param defaultValue
	 *            the value declared for when the request carries none, or null
	 * @param kind
	 *            how the message about an enum names the parameter, as in {@code query param}
	 * @param name
	 *            the name of the parameter
	 */
	public static Object convert(Class<?> type, String text, String defaultValue, String kind, String name) {
		if (text == null) {
			return defaultValue == null ? empty(type) : convertPresent(type, defaultValue, kind, name);
		}
		Object converted = convertPresent(type, text, kind, name);
		if (converted == null && defaultValue != null) {
			return convertPresent(type, defaultValue, kind, name);
		}
		return converted == null ? empty(type) : converted;
	}

	@SuppressWarnings("unchecked")
	private static Object convertPresent(Class<?> type, String text, String kind, String name) {
		if (type == String.class) {
			return text;
		}
		if (text.isEmpty()) {
			// the replaced framework read an empty value as no value at all, whatever the type
			return null;
		}
		if (type.isEnum()) {
			Enum<?> found = Enums.fromStringFuzzy(text, (Enum[]) type.getEnumConstants());
			if (found == null) {
				throw new NotConvertible(enumMessage(type, kind, name));
			}
			return found;
		}
		try {
			return number(type, text);
		} catch (NumberFormatException notANumber) {
			throw new NotConvertible(ErrorAnswers.framework(404));
		}
	}

	private static Object number(Class<?> type, String text) {
		if (type == Long.class || type == long.class) {
			return Long.valueOf(text);
		}
		if (type == Integer.class || type == int.class) {
			return Integer.valueOf(text);
		}
		if (type == Boolean.class || type == boolean.class) {
			return Boolean.valueOf(text);
		}
		throw new IllegalStateException("no conversion to " + type.getName());
	}

	/** What a parameter the request does not carry is, when nothing was declared for it. */
	private static Object empty(Class<?> type) {
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		if (type == boolean.class) {
			return Boolean.FALSE;
		}
		return null;
	}

	private static Answer enumMessage(Class<?> type, String kind, String name) {
		String constants = Arrays.stream(type.getEnumConstants())
				.map(constant -> ((Enum<?>) constant).name())
				.collect(Collectors.joining(", "));
		return Answer.status(400)
				.type(MediaType.APPLICATION_JSON)
				.entity(ErrorAnswers.message(400, kind + " " + name + " must be one of [" + constants + "]"))
				.build();
	}

	/** Raised when a value will not convert; the answer it carries is what the client is told. */
	public static final class NotConvertible extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final transient Answer answer;

		NotConvertible(Answer answer) {
			super("not convertible");
			this.answer = answer;
		}

		public Answer getAnswer() {
			return answer;
		}

	}

}
