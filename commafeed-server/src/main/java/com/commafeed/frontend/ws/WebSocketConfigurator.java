package com.commafeed.frontend.ws;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.commafeed.frontend.http.Session;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.session.SessionHelper;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;
import lombok.RequiredArgsConstructor;

/**
 * Finds the user that is opening the live-updates endpoint.
 *
 * The replaced container handed the handshake its own session; the session is now the server's own and is found from the cookie the
 * handshake carries, which is the same session under another name.
 */
@Singleton
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class WebSocketConfigurator extends Configurator {

	public static final String SESSIONKEY_USERID = "userId";

	private final WebSocketSessions webSocketSessions;
	private final SessionStore sessions;

	@Override
	public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		Session session = sessions.find(cookieOf(request.getHeaders()));
		if (session != null) {
			Optional<Long> userId = SessionHelper.getLoggedInUserId(session);
			userId.ifPresent(value -> config.getUserProperties().put(SESSIONKEY_USERID, value));
		}
	}

	private static String cookieOf(Map<String, List<String>> headers) {
		for (Map.Entry<String, List<String>> header : headers.entrySet()) {
			if ("cookie".equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty()) {
				return header.getValue().get(0);
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) {
		return (T) new WebSocketEndpoint(webSocketSessions);
	}

}
