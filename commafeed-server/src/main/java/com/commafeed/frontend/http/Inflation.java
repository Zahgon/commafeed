package com.commafeed.frontend.http;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Inflates an entity a client sent compressed, the way the replaced handler did it.
 *
 * Four things here are what a client sees rather than how it is written, and all four were measured on the replaced handler rather than
 * taken from the format:
 *
 * <ul>
 * <li>only {@code gzip} is inflated, and only when it is the first thing the encoding names, so an entity declared {@code x-gzip} or
 * {@code deflate} reaches the method as the bytes that arrived;</li>
 * <li>every member of the entity is inflated and what they hold is joined, so an entity carrying two members reaches the method as two
 * documents one after the other;</li>
 * <li>bytes after the last member that do not begin another one are ignored, and a member whose trailer is missing or short is still read,
 * because the trailer is not checked;</li>
 * <li>an entity declared compressed that does not begin as a compressed one does is a failure, not something to pass through.</li>
 * </ul>
 */
public final class Inflation {

	private static final int MAGIC_FIRST = 0x1f;
	private static final int MAGIC_SECOND = 0x8b;
	private static final int HEADER_LENGTH = 10;
	private static final int TRAILER_LENGTH = 8;

	private Inflation() {
	}

	/** Whether the encoding the request declares is the one the replaced handler inflated. */
	public static boolean inflates(String encoding) {
		if (encoding == null) {
			return false;
		}
		String first = encoding.split(",")[0].split(";")[0].trim();
		return "gzip".equalsIgnoreCase(first.toLowerCase(Locale.ROOT));
	}

	/** The entity as it was before it was compressed. */
	public static byte[] inflate(byte[] entity) {
		if (entity.length == 0) {
			return entity;
		}
		if (!beginsAMember(entity, 0)) {
			throw new IllegalArgumentException("the entity does not begin as a compressed one does");
		}
		ByteArrayOutputStream inflated = new ByteArrayOutputStream(entity.length * 4);
		int at = 0;
		while (at < entity.length && beginsAMember(entity, at)) {
			at = oneMember(entity, at, inflated);
		}
		return inflated.toByteArray();
	}

	private static boolean beginsAMember(byte[] entity, int at) {
		return at + 1 < entity.length && (entity[at] & 0xff) == MAGIC_FIRST && (entity[at + 1] & 0xff) == MAGIC_SECOND;
	}

	/** Inflates one member and answers where the next one could begin. */
	private static int oneMember(byte[] entity, int at, ByteArrayOutputStream inflated) {
		int from = at + HEADER_LENGTH;
		if (from >= entity.length) {
			return entity.length;
		}
		Inflater inflater = new Inflater(true);
		inflater.setInput(entity, from, entity.length - from);
		byte[] buffer = new byte[8192];
		try {
			while (!inflater.finished()) {
				int read = inflater.inflate(buffer);
				if (read == 0) {
					// the member was cut short: what it held so far is what the method is handed
					break;
				}
				inflated.write(buffer, 0, read);
			}
			return from + (int) inflater.getBytesRead() + TRAILER_LENGTH;
		} catch (DataFormatException notCompressed) {
			throw new IllegalArgumentException("the entity is not compressed as it says it is", notCompressed);
		} finally {
			inflater.end();
		}
	}

}
