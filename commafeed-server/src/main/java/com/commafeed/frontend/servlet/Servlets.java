package com.commafeed.frontend.servlet;

import java.util.LinkedHashMap;
import java.util.Map;

import com.commafeed.frontend.http.Reply;
import com.commafeed.frontend.http.Request;

/**
 * The surfaces the application registers beside the API, each on one target of its own.
 *
 * The replaced container matched one of these on the exact target it was registered on, which is why the same target with a trailing slash,
 * or in another letter case, reaches none of them. All of them answer a plain read only; what the container answered for the other methods
 * is the same for these as for the web client's own files, and is decided where a request is routed.
 */
public final class Servlets {

	private final Map<String, Servlet> registered = new LinkedHashMap<>();

	public void register(String target, Servlet servlet) {
		registered.put(target, servlet);
	}

	public boolean claims(String path) {
		return registered.containsKey(path);
	}

	public Reply serve(Request request, String path) {
		return registered.get(path).get(request);
	}

	/**
	 * An answer carrying text, with the character set the container put on one.
	 *
	 * The container added the character set when the servlet wrote its first character, so an answer with nothing written into it - which
	 * is what a request asking only for the headers gets - says what it is without one.
	 */
	public static Reply text(Request request, String mediaType, String content) {
		if ("HEAD".equals(request.getMethod())) {
			return Reply.of(200).contentType(mediaType);
		}
		return Reply.of(200).contentType(mediaType + ";charset=iso-8859-1").entity(content);
	}

	/** What one of these surfaces answers to a read. */
	public interface Servlet {

		Reply get(Request request);

	}

}
