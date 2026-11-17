package com.commafeed.frontend.http;

/**
 * How long an answer may be kept, written the way the replaced runtime wrote it.
 *
 * The favicon surface builds one of these, and what it writes is what a browser reads, so the spelling is kept: the directives in the order
 * the replaced runtime wrote them, separated by a comma and a space, with the transformation directive first.
 */
public final class CacheControl {

	private boolean noTransform = true;
	private boolean isPrivate;
	private int maxAge = -1;

	public void setNoTransform(boolean value) {
		this.noTransform = value;
	}

	public void setPrivate(boolean value) {
		this.isPrivate = value;
	}

	public void setMaxAge(int seconds) {
		this.maxAge = seconds;
	}

	@Override
	public String toString() {
		StringBuilder written = new StringBuilder();
		if (isPrivate) {
			written.append("private");
		}
		if (noTransform) {
			append(written, "no-transform");
		}
		if (maxAge >= 0) {
			append(written, "max-age=" + maxAge);
		}
		return written.toString();
	}

	private static void append(StringBuilder written, String directive) {
		if (written.length() > 0) {
			written.append(", ");
		}
		written.append(directive);
	}

}
