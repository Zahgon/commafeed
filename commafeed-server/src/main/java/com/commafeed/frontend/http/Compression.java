package com.commafeed.frontend.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Compresses an answer, and says that the answer depends on whether the client asked for it.
 *
 * The replaced handler decided this in a fixed order, and a client sees every step of it, so the order is kept:
 *
 * <ol>
 * <li>the request's method is not one the handler was configured for - only a read and a post were - and the handler stepped aside without
 * saying anything about it;</li>
 * <li>the target's own file name says it is a kind of thing that is already compressed, and again the handler stepped aside silently, which
 * is why an icon's answer says nothing about encoding;</li>
 * <li>the answer's status is not a plain success, and the handler stepped aside silently;</li>
 * <li>the answer says it is a kind of thing that is already compressed, and the handler stepped aside silently;</li>
 * <li>otherwise the answer is marked as depending on what the client accepts - whether or not it is then compressed;</li>
 * <li>and it is compressed only if the client accepts it and the entity is at least as large as the smallest the handler was configured to
 * bother with.</li>
 * </ol>
 */
public final class Compression {

	/** The smallest entity the replaced handler compressed. */
	public static final int SMALLEST = 256;

	/** The methods the replaced handler was configured for. */
	private static final Set<String> METHODS = Set.of("GET", "POST");

	/** The kinds of thing the replaced handler left alone because they are already compressed. */
	private static final Set<String> ALREADY_COMPRESSED = Set.of("application/compress", "application/zip", "application/gzip",
			"application/bzip2", "application/brotli", "application/x-xz", "application/x-rar-compressed", "application/zstd");

	private Compression() {
	}

	public static void apply(Request request, Reply reply) {
		if (!METHODS.contains(request.getMethod())) {
			return;
		}
		String byExtension = MimeTypes.byExtension(request.getTarget().getMatchedPath());
		if (byExtension != null && !compressible(byExtension)) {
			return;
		}
		int status = reply.getStatus();
		if (status < 200 || status == 204 || status == 205 || status >= 300) {
			return;
		}
		String written = reply.getHeader("Content-Type");
		if (written != null && !compressible(written.split(";")[0].trim())) {
			return;
		}
		reply.header("Vary", "Accept-Encoding");
		if (accepted(request) && reply.getEntity().length >= SMALLEST) {
			reply.setHeader("Content-Encoding", "gzip");
			reply.entity(compress(reply.getEntity()));
		}
	}

	private static boolean compressible(String mediaType) {
		String type = mediaType.toLowerCase(Locale.ROOT);
		if (type.startsWith("image/") || type.startsWith("audio/") || type.startsWith("video/")) {
			return false;
		}
		return !ALREADY_COMPRESSED.contains(type);
	}

	/**
	 * Whether the client accepts a compressed answer.
	 *
	 * Only the name {@code gzip} counts: neither the wildcard nor {@code x-gzip} selected it. A weight of zero turns it down; a weight that
	 * is not a number at all was not read as turning it down.
	 */
	public static boolean accepted(Request request) {
		List<String> values = request.getHeaders("accept-encoding");
		for (String value : values) {
			for (String entry : value.split(",")) {
				String[] parts = entry.split(";");
				if (!"gzip".equalsIgnoreCase(parts[0].trim())) {
					continue;
				}
				if (weight(parts) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static double weight(String[] parts) {
		for (int at = 1; at < parts.length; at++) {
			String[] pair = parts[at].split("=", 2);
			if (pair.length == 2 && "q".equalsIgnoreCase(pair[0].trim())) {
				try {
					return Double.parseDouble(pair[1].trim());
				} catch (NumberFormatException notANumber) {
					return 1;
				}
			}
		}
		return 1;
	}

	private static byte[] compress(byte[] entity) {
		ByteArrayOutputStream compressed = new ByteArrayOutputStream(entity.length / 2 + 32);
		try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
			gzip.write(entity);
		} catch (IOException impossible) {
			throw new UncheckedIOException(impossible);
		}
		return compressed.toByteArray();
	}

}
