package com.commafeed.frontend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole.Role;
import com.commafeed.backend.service.UserService;
import com.commafeed.frontend.http.Answer;
import com.commafeed.frontend.http.AnswerException;
import com.commafeed.frontend.http.MediaType;
import com.commafeed.frontend.http.Request;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.http.Status;
import com.commafeed.frontend.session.SessionHelper;

import lombok.RequiredArgsConstructor;

/**
 * Answers the user a request is made by, for a resource method that asks for one.
 *
 * The replaced runtime asked this while it was building the method's arguments, which is why a request with no credentials is answered that
 * credentials are required even when something else about it - the entity it carries, say - would not have been readable either.
 *
 * Three credentials are looked at, in this order, and each of them is what the replaced code looked at: an API key in the query, which only
 * some methods allow; a name and password written into the {@code Authorization} header, where the scheme is read without regard to case
 * and the value is read as it was written, so a name or password with a blank around it is not the one without it; and the session the
 * request's cookie names.
 */
@RequiredArgsConstructor
public class SecurityChecks {

	private static final String PREFIX = "Basic";

	private final UserDAO userDAO;
	private final UserService userService;
	private final CommaFeedConfiguration config;
	private final SessionStore sessions;

	public User check(Request request, SecurityCheck check) {
		Optional<User> user = apiKeyLogin(request, check.apiKeyAllowed());
		if (user.isEmpty()) {
			user = basicAuthenticationLogin(request);
		}
		if (user.isEmpty()) {
			user = cookieSessionLogin(new SessionHelper(request, sessions));
		}

		if (user.isPresent()) {
			Set<Role> roles = userService.getRoles(user.get());
			if (roles.contains(check.value())) {
				return user.get();
			}
			throw refused(Status.FORBIDDEN, "You don't have the required role to access this resource.");
		}
		throw refused(Status.UNAUTHORIZED, "Credentials are required to access this resource.");
	}

	Optional<User> cookieSessionLogin(SessionHelper sessionHelper) {
		Optional<User> loggedInUser = sessionHelper.getLoggedInUserId().map(userDAO::findById);
		loggedInUser.ifPresent(userService::performPostLoginActivities);
		return loggedInUser;
	}

	private Optional<User> basicAuthenticationLogin(Request request) {
		String header = request.getHeader("Authorization");
		if (header != null) {
			int space = header.indexOf(' ');
			if (space > 0) {
				String method = header.substring(0, space);
				if (PREFIX.equalsIgnoreCase(method)) {
					byte[] decodedBytes = Base64.getDecoder().decode(header.substring(space + 1));
					String decoded = new String(decodedBytes, StandardCharsets.ISO_8859_1);
					int colon = decoded.indexOf(':');
					if (colon > 0) {
						String username = decoded.substring(0, colon);
						String password = decoded.substring(colon + 1);
						return userService.login(username, password);
					}
				}
			}
		}
		return Optional.empty();
	}

	private Optional<User> apiKeyLogin(Request request, boolean apiKeyAllowed) {
		String apiKey = request.getTarget().getQueryParameter("apiKey");
		if (apiKey != null && apiKeyAllowed) {
			return userService.login(apiKey);
		}
		return Optional.empty();
	}

	private AnswerException refused(Status status, String message) {
		Map<String, Object> entity = new LinkedHashMap<>();
		entity.put("message", message);
		entity.put("allowRegistrations", config.getApplicationSettings().getAllowRegistrations());
		return new AnswerException(Answer.status(status.getCode()).type(MediaType.APPLICATION_JSON).entity(entity).build());
	}

}
