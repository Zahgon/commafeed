package com.commafeed.frontend.session;

import com.commafeed.config.Duration;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

/**
 * The {@code session} section of the configuration file.
 *
 * The replaced container's session handler read these and they are what a client sees of a session: how long the cookie it is given lives,
 * and how long a session is kept without being used. The handler also kept its sessions in the database through a store of its own; the
 * server keeps them itself now, so the two settings about that store are gone with it.
 */
@Getter
public class SessionHandlerFactory {

	@JsonProperty
	private Duration cookieMaxAge = Duration.days(30);

	@JsonProperty
	private Duration maxInactiveInterval = Duration.days(30);

}
