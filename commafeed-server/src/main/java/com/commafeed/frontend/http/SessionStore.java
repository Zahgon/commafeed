package com.commafeed.frontend.http;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.commafeed.frontend.session.SessionHandlerFactory;

/**
 * The sessions the server has handed out, and the cookie that carries one.
 *
 * What a client sees of a session is the cookie, and everything about it comes from what the replaced container's session handler was
 * configured with: cookies only, so nothing is ever written into a target; the path the whole application is under; an expiry and a maximum
 * age taken from the configuration; and nothing a script can read. The cookie is written onto whatever answer the request ends with, which
 * is why a request that is given a session while failing still carries one back.
 */
public final class SessionStore {

	public static final String COOKIE_NAME = "JSESSIONID";

	private static final char[] ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
	private static final int ID_LENGTH = 32;

	private final SecureRandom random = new SecureRandom();
	private final Map<String, Session> sessions = new ConcurrentHashMap<>();
	private final SessionHandlerFactory settings;

	public SessionStore(SessionHandlerFactory settings) {
		this.settings = settings;
	}

	/** The session the request's cookie names, or null when it names none that is still here. */
	public Session find(Request request) {
		if (request.getSession() != null) {
			return request.getSession();
		}
		String id = request.getCookie(COOKIE_NAME);
		if (id == null || id.isEmpty()) {
			return null;
		}
		Session found = sessions.get(id);
		if (found == null || !found.isValid()) {
			return null;
		}
		request.setSession(found, false);
		return found;
	}

	/** The session a cookie header names, or null when it names none that is still here. */
	public Session find(String cookieHeader) {
		if (cookieHeader == null) {
			return null;
		}
		for (String pair : cookieHeader.split(";")) {
			int equals = pair.indexOf('=');
			if (equals > 0 && pair.substring(0, equals).trim().equals(COOKIE_NAME)) {
				Session found = sessions.get(pair.substring(equals + 1).trim());
				return found != null && found.isValid() ? found : null;
			}
		}
		return null;
	}

	/** The session the request already has, or a new one, whose cookie the answer then carries. */
	public Session findOrCreate(Request request) {
		Session found = find(request);
		if (found != null) {
			return found;
		}
		StringBuilder id = new StringBuilder(ID_LENGTH);
		for (int at = 0; at < ID_LENGTH; at++) {
			id.append(ID_CHARACTERS[random.nextInt(ID_CHARACTERS.length)]);
		}
		Session created = new Session(id.toString());
		sessions.put(created.getId(), created);
		request.setSession(created, true);
		return created;
	}

	/** Drops a session; the answer still carries the cookie of a session made by this request. */
	public void invalidate(Session session) {
		if (session != null) {
			session.invalidate();
			sessions.remove(session.getId());
		}
	}

	/** Writes the cookie for a session this request was given, if it was given one. */
	public void writeCookie(Request request, Reply reply) {
		if (!request.isSessionFresh()) {
			return;
		}
		long maxAge = settings.getCookieMaxAge().toSeconds();
		reply.header("Set-Cookie", COOKIE_NAME + "=" + request.getSession().getId() + "; Path=/; Expires="
				+ Http.formatDate(Instant.now().plusSeconds(maxAge)) + "; Max-Age=" + maxAge + "; HttpOnly");
	}

}
