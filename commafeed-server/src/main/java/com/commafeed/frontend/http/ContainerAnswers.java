package com.commafeed.frontend.http;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The answers the container wrote by itself, before or around the layer that dispatches a request.
 *
 * These are not the framework's error documents: they are what the replaced servlet container put on the wire when it answered a request
 * without the application deciding anything, and a client can tell them apart from the framework's. There are three of them:
 *
 * <ul>
 * <li>a status with nothing at all on it, not even a content type, which is what a method the container does not dispatch and a file that
 * is not there were answered with;</li>
 * <li>the container's own page about an error, which carries the target, the status and a message, and which the container marked as not to
 * be cached;</li>
 * <li>the same page written as a JSON document, which the container wrote when the request asked for JSON and the request itself was the
 * thing that was wrong.</li>
 * </ul>
 */
public final class ContainerAnswers {

	private ContainerAnswers() {
	}

	/** A status with nothing on it: no entity, no content type and no header of any kind. */
	public static Reply bare(int code) {
		return Reply.of(code);
	}

	/** The container's own page about an error, which it marked as not to be cached. */
	public static Reply page(int code, String message, String target) {
		String text = "<html>\n<head>\n" + "<meta http-equiv=\"Content-Type\" content=\"text/html;charset=ISO-8859-1\"/>\n"
				+ "<title>Error " + code + " " + message + "</title>\n" + "</head>\n" + "<body><h2>HTTP ERROR " + code + " " + message
				+ "</h2>\n" + "<table>\n" + "<tr><th>URI:</th><td>" + target + "</td></tr>\n" + "<tr><th>STATUS:</th><td>" + code
				+ "</td></tr>\n" + "<tr><th>MESSAGE:</th><td>" + message + "</td></tr>\n" + "</table>\n\n</body>\n</html>\n";
		return Reply.of(code).setHeader("Cache-Control", CacheBusting.NO_CACHE).contentType("text/html;charset=iso-8859-1").entity(text);
	}

	/** The same page as a JSON document, which is what the container wrote when JSON was asked for. */
	public static Reply jsonPage(int code, String message, String target) {
		String text = "{\n\"message\":\"" + message + "\",\n\"url\":\"" + target + "\",\n\"status\":\"" + code + "\"\n}\n";
		return Reply.of(code).setHeader("Cache-Control", CacheBusting.NO_CACHE).contentType(MediaType.APPLICATION_JSON).entity(text);
	}

	/**
	 * The container's own page about an error, written as the request said it would read one.
	 *
	 * The container looked at what the request accepts and wrote the page as the first of those it had a page for. Two things follow from
	 * that and a client sees both: a request that accepts JSON is answered with the page as a JSON document rather than as a document to
	 * look at, and a request that accepts something the container had no page for - including one whose offers it could not read at all -
	 * is answered with the status and nothing else.
	 */
	public static Reply errorPage(Request request, int code, String message) {
		String target = request.getTarget().getMatchedPath();
		if (!request.hasHeader("accept")) {
			return page(code, message, target);
		}
		for (String accepted : leniently(request)) {
			if (accepted.equals(MediaType.APPLICATION_JSON) || accepted.equals("text/json")) {
				return jsonPage(code, message, target);
			}
			if (accepted.equals(MediaType.TEXT_HTML) || accepted.equals("text/*") || accepted.equals(MediaType.WILDCARD)) {
				return page(code, message, target);
			}
		}
		return Reply.of(code);
	}

	/**
	 * What the request accepts, read the forgiving way the container read it: an offer whose weight is not a number, or is zero, is
	 * dropped, and everything else is kept as it was written.
	 */
	private static List<String> leniently(Request request) {
		List<String> accepted = new ArrayList<>();
		for (String value : request.getHeaders("accept")) {
			for (String entry : value.split(",", -1)) {
				String text = entry.trim();
				if (text.isEmpty()) {
					continue;
				}
				int semicolon = text.indexOf(';');
				if (semicolon >= 0) {
					Double weight = weightOf(text.substring(semicolon + 1));
					if (weight == null || weight <= 0) {
						continue;
					}
					text = text.substring(0, semicolon).trim();
				}
				accepted.add(text.toLowerCase(Locale.ROOT));
			}
		}
		return accepted;
	}

	private static Double weightOf(String parameters) {
		for (String parameter : parameters.split(";")) {
			String[] pair = parameter.split("=", 2);
			if (pair.length == 2 && "q".equals(pair[0].trim())) {
				try {
					return Double.valueOf(pair[1].trim());
				} catch (NumberFormatException notANumber) {
					return null;
				}
			}
		}
		return 1d;
	}

	/**
	 * The page the container wrote for a target whose spelling it rejected outright.
	 *
	 * It carries no message, and unlike the others the container had not yet reached the filter that marks an answer as not to be cached,
	 * so this one is not marked.
	 */
	public static Reply badTarget() {
		Reply page = page(400, "", "");
		page.removeHeader("Cache-Control");
		return page;
	}

}
