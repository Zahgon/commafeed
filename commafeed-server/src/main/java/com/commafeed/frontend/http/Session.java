package com.commafeed.frontend.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One session: an identifier the cookie carries and the attributes a request put on it.
 *
 * The replaced container owned the session and kept it in the database through a store of its own. Only two things about it are visible to
 * a client - the cookie that carries its identifier and whether a later request is recognised by it - so the session lives in the server's
 * own store here rather than in the database.
 */
public final class Session {

	private final String id;
	private final Map<String, Object> attributes = new ConcurrentHashMap<>();
	private volatile boolean valid = true;

	Session(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	public Object getAttribute(String name) {
		return attributes.get(name);
	}

	public void setAttribute(String name, Object value) {
		if (value == null) {
			attributes.remove(name);
		} else {
			attributes.put(name, value);
		}
	}

	public boolean isValid() {
		return valid;
	}

	void invalidate() {
		valid = false;
		attributes.clear();
	}

}
