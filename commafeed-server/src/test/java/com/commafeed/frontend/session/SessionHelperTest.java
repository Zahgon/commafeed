package com.commafeed.frontend.session;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.RequestTarget;
import com.commafeed.frontend.http.Session;
import com.commafeed.frontend.http.SessionStore;

class SessionHelperTest {

	private static Request request() {
		return new Request("GET", RequestTarget.of("/", null), java.util.Map.of(), new byte[0]);
	}

	@Test
	void gettingUserDoesNotCreateSession() {
		SessionStore sessions = Mockito.mock(SessionStore.class);
		Request request = request();

		new SessionHelper(request, sessions).getLoggedInUserId();

		Mockito.verify(sessions).find(request);
		Mockito.verify(sessions, Mockito.never()).findOrCreate(request);
	}

	@Test
	void gettingUserShouldNotReturnUserIfThereIsNoPreexistingSession() {
		SessionStore sessions = Mockito.mock(SessionStore.class);
		Mockito.when(sessions.find(Mockito.any(Request.class))).thenReturn(null);

		Optional<Long> userId = new SessionHelper(request(), sessions).getLoggedInUserId();

		Assertions.assertFalse(userId.isPresent());
	}

	@Test
	void gettingUserShouldNotReturnUserIfUserNotPresentInSession() {
		Session session = Mockito.mock(Session.class);
		Mockito.when(session.getAttribute(SessionHelper.SESSION_KEY_USER_ID)).thenReturn(null);
		SessionStore sessions = Mockito.mock(SessionStore.class);
		Mockito.when(sessions.find(Mockito.any(Request.class))).thenReturn(session);

		Optional<Long> userId = new SessionHelper(request(), sessions).getLoggedInUserId();

		Assertions.assertFalse(userId.isPresent());
	}

	@Test
	void gettingUserShouldReturnUserIfUserPresentInSession() {
		Session session = Mockito.mock(Session.class);
		Mockito.when(session.getAttribute(SessionHelper.SESSION_KEY_USER_ID)).thenReturn(1L);
		SessionStore sessions = Mockito.mock(SessionStore.class);
		Mockito.when(sessions.find(Mockito.any(Request.class))).thenReturn(session);

		Optional<Long> userId = new SessionHelper(request(), sessions).getLoggedInUserId();

		Assertions.assertTrue(userId.isPresent());
	}

}
