package com.commafeed.frontend.servlet;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.frontend.http.Reply;
import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.http.Status;
import com.commafeed.frontend.session.SessionHelper;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

/**
 * Drops the session and sends the client back to the application.
 *
 * Asking for the session is what makes one when the request arrived with none, so the answer carries a cookie for a session that is dropped
 * in the same breath - which is what the replaced container did, because it had already written the cookie by then.
 */
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class LogoutServlet implements Servlets.Servlet {

	private final CommaFeedConfiguration config;
	private final SessionStore sessions;

	@Override
	public Reply get(Request request) {
		new SessionHelper(request, sessions).invalidate();
		return Reply.of(Status.MOVED_TEMPORARILY.getCode()).setHeader("Location", config.getApplicationSettings().getPublicUrl());
	}

}
