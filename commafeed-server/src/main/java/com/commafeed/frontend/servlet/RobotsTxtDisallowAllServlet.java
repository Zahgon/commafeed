package com.commafeed.frontend.servlet;

import com.commafeed.frontend.http.Reply;
import com.commafeed.frontend.http.Request;

import jakarta.inject.Singleton;

/**
 * Tells web crawlers to stay away. Registered only when the configuration asks for it.
 */
@Singleton
public class RobotsTxtDisallowAllServlet implements Servlets.Servlet {

	@Override
	public Reply get(Request request) {
		return Servlets.text(request, "text/plain", "User-agent: *\nDisallow: /");
	}

}
