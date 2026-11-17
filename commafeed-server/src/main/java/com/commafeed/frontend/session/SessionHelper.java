package com.commafeed.frontend.session;

import java.util.Optional;

import com.commafeed.backend.model.User;
import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.Session;
import com.commafeed.frontend.http.SessionStore;

import lombok.RequiredArgsConstructor;

/**
 * Reads and writes the logged-in user on the session a request arrived with.
 *
 * The session used to be the replaced container's, reached through the request it handed the resource method; it is now the server's own,
 * reached through the store, and what is kept on it is the same thing under the same name.
 */
@RequiredArgsConstructor
public class SessionHelper {

	public static final String SESSION_KEY_USER_ID = "user-id";

	private final Request request;
	private final SessionStore sessions;

	public Optional<Long> getLoggedInUserId() {
		return getLoggedInUserId(sessions.find(request));
	}

	public static Optional<Long> getLoggedInUserId(Session session) {
		if (session == null) {
			return Optional.empty();
		}
		Long userId = (Long) session.getAttribute(SESSION_KEY_USER_ID);
		return Optional.ofNullable(userId);
	}

	/**
	 * Puts the user on the session, making one when the request arrived with none.
	 *
	 * A session made here is recorded on the request, and the answer the request ends with carries its cookie, whatever that answer turns
	 * out to be.
	 */
	public void setLoggedInUser(User user) {
		sessions.findOrCreate(request).setAttribute(SESSION_KEY_USER_ID, user.getId());
	}

	/** Drops the session this request arrived with, after making one if it arrived with none. */
	public void invalidate() {
		sessions.invalidate(sessions.findOrCreate(request));
	}

}
