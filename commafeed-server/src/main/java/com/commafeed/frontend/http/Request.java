package com.commafeed.frontend.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One request as the serving layer reads it: the method, the target, the headers and the entity.
 *
 * The entity is read in full before anything is dispatched, because the request may have arrived compressed and because a resource method
 * runs on a worker thread rather than on the thread the web server read the request on.
 */
public final class Request {

	private final String method;
	private final RequestTarget target;
	private final Map<String, List<String>> headers;
	private byte[] entity;
	private Session session;
	private boolean sessionFresh;

	public Request(String method, RequestTarget target, Map<String, List<String>> headers, byte[] entity) {
		this.method = method;
		this.target = target;
		Map<String, List<String>> kept = new LinkedHashMap<>();
		// the replaced container handed a header's value over without the blanks around it, and
		// what it handed over is what a client sees: a credential written with a trailing blank is
		// read as one without it
		headers.forEach((name, values) -> kept.put(name.toLowerCase(Locale.ROOT),
				values.stream().map(String::trim).collect(Collectors.toUnmodifiableList())));
		this.headers = Collections.unmodifiableMap(kept);
		this.entity = entity == null ? new byte[0] : entity;
	}

	public String getMethod() {
		return method;
	}

	public RequestTarget getTarget() {
		return target;
	}

	/** Every value of a header the request repeats, in the order it carried them. */
	public List<String> getHeaders(String name) {
		return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
	}

	/** The first value of a header, or null when the request does not carry it. */
	public String getHeader(String name) {
		List<String> values = getHeaders(name);
		return values.isEmpty() ? null : values.get(0);
	}

	public boolean hasHeader(String name) {
		return !getHeaders(name).isEmpty();
	}

	public byte[] getEntity() {
		return entity;
	}

	public void setEntity(byte[] bytes) {
		this.entity = bytes == null ? new byte[0] : bytes;
	}

	public String getEntityAsText() {
		return new String(entity, StandardCharsets.UTF_8);
	}

	/** The session this request arrived with or was given, or null while it has none. */
	public Session getSession() {
		return session;
	}

	void setSession(Session value, boolean fresh) {
		this.session = value;
		this.sessionFresh = fresh;
	}

	/** Whether the session was made for this request, so that its answer carries the cookie. */
	public boolean isSessionFresh() {
		return sessionFresh;
	}

	/** The value a cookie of the given name carries, taken from every Cookie header. */
	public String getCookie(String name) {
		for (String header : getHeaders("cookie")) {
			for (String pair : header.split(";")) {
				int equals = pair.indexOf('=');
				if (equals > 0 && pair.substring(0, equals).trim().equals(name)) {
					return pair.substring(equals + 1).trim();
				}
			}
		}
		return null;
	}

	/** The names of the cookies the request carries, for the parts that only need to know. */
	public List<String> getCookieNames() {
		List<String> names = new ArrayList<>();
		for (String header : getHeaders("cookie")) {
			for (String pair : header.split(";")) {
				int equals = pair.indexOf('=');
				names.add((equals > 0 ? pair.substring(0, equals) : pair).trim());
			}
		}
		return names;
	}

}
