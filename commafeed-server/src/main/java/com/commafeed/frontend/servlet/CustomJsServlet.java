package com.commafeed.frontend.servlet;

import com.commafeed.backend.dao.UnitOfWork;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.dao.UserSettingsDAO;
import com.commafeed.backend.model.UserSettings;
import com.commafeed.frontend.http.SessionStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CustomJsServlet extends AbstractCustomCodeServlet {

	@Inject
	public CustomJsServlet(UnitOfWork unitOfWork, UserDAO userDAO, UserSettingsDAO userSettingsDAO, SessionStore sessions) {
		super(unitOfWork, userDAO, userSettingsDAO, sessions);
	}

	@Override
	protected String getMimeType() {
		return "application/javascript";
	}

	@Override
	protected String getCustomCode(UserSettings settings) {
		return settings.getCustomJs();
	}

}
