package com.commafeed.frontend.http;

/**
 * Turns text into an enum constant the forgiving way the replaced framework did it.
 *
 * The replaced framework matched a constant after removing every space, newline, carriage return and tab from the text and mapping
 * {@code -} and {@code .} onto {@code _}, and then compared without regard to case - first against each constant's name, then against what
 * each constant writes itself as. Both a query or path parameter and a value inside a JSON entity were read this way, so both go through
 * here.
 */
public final class Enums {

	private Enums() {
	}

	/** The constant the text names, or null when it names none of them. */
	public static <T extends Enum<T>> T fromStringFuzzy(String value, T[] constants) {
		if (value == null) {
			return null;
		}
		String text = value.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "").replace('-', '_').replace('.', '_');
		for (T constant : constants) {
			if (constant.name().equalsIgnoreCase(text)) {
				return constant;
			}
		}
		for (T constant : constants) {
			if (constant.toString().equalsIgnoreCase(text)) {
				return constant;
			}
		}
		return null;
	}

}
