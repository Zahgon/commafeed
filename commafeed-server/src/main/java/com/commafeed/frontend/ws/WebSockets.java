package com.commafeed.frontend.ws;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.frontend.http.SessionStore;
import com.google.inject.Injector;

import io.helidon.webserver.Routing;
import io.helidon.webserver.websocket.WebSocketRouting;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * The endpoint the web client keeps open to be told when something changed.
 *
 * The replaced container ran this endpoint inside itself and handed it the session of the request that opened it. Helidon runs it on a
 * routing of its own, and the session is now found from the cookie the opening request carries, which is the same session under another
 * name.
 *
 * It is registered only when the configuration asks for it, exactly as before.
 */
public final class WebSockets {

	/** Where the endpoint answers. */
	public static final String PATH = "/ws";

	private WebSockets() {
	}

	/** The routing for the endpoint, or null when the configuration switches it off. */
	public static Routing routing(CommaFeedConfiguration config, SessionStore sessions, Injector injector) {
		if (!Boolean.TRUE.equals(config.getApplicationSettings().getWebsocketEnabled())) {
			return null;
		}
		WebSocketConfigurator configurator = new WebSocketConfigurator(injector.getInstance(WebSocketSessions.class), sessions);
		return WebSocketRouting.builder()
				.endpoint(ServerEndpointConfig.Builder.create(WebSocketEndpoint.class, PATH).configurator(configurator).build())
				.build();
	}

}
