package com.commafeed.frontend.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.time.Instant;
import java.util.Arrays;

/**
 * Serves the files the web client ships, from the class path, the way the replaced bundle did.
 *
 * The replaced bundle was given the class path directory the client's build puts its files in, the target to serve them under and the name
 * of the document to serve for a target that names a directory. Five of the things it did are what a client sees:
 *
 * <ul>
 * <li>a file that is not there is answered with the status alone - no content type, no entity and no header of any kind;</li>
 * <li>what a file is said to be comes from the container's own table of file names, and anything the table says nothing about is said to be
 * a document to look at;</li>
 * <li>a character set is put on every answer whatever the file is, including on an icon;</li>
 * <li>a client may ask for a range of the file and is answered with that part of it;</li>
 * <li>and anything at all that goes wrong while answering - including a date the client sent that does not read as one - is answered as a
 * file that is not there.</li>
 * </ul>
 */
public final class Assets {

	/** Where on the class path the client's files are, and the document a directory is served as. */
	private static final String ROOT = "assets";
	private static final String INDEX = "index.html";
	private static final String CHARSET = "utf-8";

	private Assets() {
	}

	/** Whether the server has a file for this target at all. */
	public static boolean has(String path) {
		return locate(path) != null;
	}

	public static Reply serve(Request request, String path) {
		URL found = locate(path);
		if (found == null) {
			return ContainerAnswers.bare(404);
		}
		try {
			return read(request, path, found);
		} catch (IOException | RuntimeException wentWrong) {
			return ContainerAnswers.bare(404);
		}
	}

	/**
	 * The file this target names, or null when there is none.
	 *
	 * A target that ends in a slash names the document a directory is served as; a target that names a directory itself has no file, which
	 * is why asking for one is answered as a file that is not there rather than with a listing.
	 */
	private static URL locate(String path) {
		String base = ROOT + path;
		URL found = null;
		if (base.endsWith("/")) {
			found = resource(base + INDEX);
		}
		if (found == null) {
			String trimmed = base;
			while (trimmed.endsWith("/")) {
				trimmed = trimmed.substring(0, trimmed.length() - 1);
			}
			found = resource(trimmed);
			if (found != null && resource(trimmed + "/") != null) {
				return null;
			}
		}
		return found;
	}

	private static URL resource(String name) {
		return Thread.currentThread().getContextClassLoader() == null ? Assets.class.getResource("/" + name)
				: Thread.currentThread().getContextClassLoader().getResource(name);
	}

	private static Reply read(Request request, String path, URL found) throws IOException {
		URLConnection connection = found.openConnection();
		connection.setUseCaches(false);
		byte[] content;
		try (InputStream stream = connection.getInputStream()) {
			content = stream.readAllBytes();
		}
		long modified = connection.getLastModified();
		String since = request.getHeader("If-Modified-Since");
		if (since != null) {
			Instant when = Http.parseDate(since);
			if (when == null) {
				// the replaced servlet answered anything it could not make sense of as a miss
				return ContainerAnswers.bare(404);
			}
			if (modified > 0 && when.toEpochMilli() >= modified / 1000 * 1000) {
				return Reply.of(304);
			}
		}
		String mediaType = MimeTypes.byExtension(path);
		Reply reply = Reply.of(200).contentType((mediaType == null ? MimeTypes.DEFAULT : mediaType) + ";charset=" + CHARSET);
		if (modified > 0) {
			reply.setHeader("Last-Modified", Http.formatDate(Instant.ofEpochMilli(modified)));
		}
		return range(request, reply, content);
	}

	/** The part of the file a client asked for, or the whole of it when it asked for no part. */
	private static Reply range(Request request, Reply reply, byte[] content) {
		String asked = request.getHeader("Range");
		if (asked == null || !asked.startsWith("bytes=") || asked.contains(",")) {
			return reply.entity(content);
		}
		String[] bounds = asked.substring("bytes=".length()).split("-", -1);
		try {
			int from = bounds[0].isEmpty() ? content.length - Integer.parseInt(bounds[1]) : Integer.parseInt(bounds[0]);
			int to = bounds.length < 2 || bounds[1].isEmpty() || bounds[0].isEmpty() ? content.length - 1 : Integer.parseInt(bounds[1]);
			if (from < 0 || from >= content.length) {
				return reply.entity(content);
			}
			int last = Math.min(to, content.length - 1);
			return reply.status(206)
					.setHeader("Content-Range", "bytes " + from + "-" + last + "/" + content.length)
					.entity(Arrays.copyOfRange(content, from, last + 1));
		} catch (NumberFormatException notARange) {
			return reply.entity(content);
		}
	}

}
