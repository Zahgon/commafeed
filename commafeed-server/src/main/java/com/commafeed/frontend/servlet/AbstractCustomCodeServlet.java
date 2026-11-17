package com.commafeed.frontend.servlet;

import java.util.Optional;

import com.commafeed.backend.dao.UnitOfWork;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserSettingsDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserSettings;
import com.commafeed.frontend.http.Reply;
import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.session.SessionHelper;

import lombok.RequiredArgsConstructor;

/**
 * Serves the code a user has saved for the web client to run.
 *
 * The answer says what it is whether or not there is anything to serve, and a character set is put on it only when something is written -
 * which is what the replaced container did, because it added one when the servlet first wrote a character and not before.
 */
@RequiredArgsConstructor
abstract class AbstractCustomCodeServlet implements Servlets.Servlet {

	private final UnitOfWork unitOfWork;
	private final UserDAO userDAO;
	private final UserSettingsDAO userSettingsDAO;
	private final SessionStore sessions;

	@Override
	public final Reply get(Request request) {
		Reply reply = Reply.of(200).contentType(getMimeType());

		SessionHelper sessionHelper = new SessionHelper(request, sessions);
		Optional<Long> userId = sessionHelper.getLoggedInUserId();
		final Optional<User> user = unitOfWork.call(() -> userId.map(userDAO::findById));
		if (user.isEmpty()) {
			return reply;
		}

		UserSettings settings = unitOfWork.call(() -> userSettingsDAO.findByUser(user.get()));
		if (settings == null) {
			return reply;
		}

		String customCode = getCustomCode(settings);
		if (customCode == null) {
			return reply;
		}

		return Servlets.text(request, getMimeType(), customCode);
	}

	protected abstract String getMimeType();

	protected abstract String getCustomCode(UserSettings settings);

}
