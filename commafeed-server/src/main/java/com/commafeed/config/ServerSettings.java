package com.commafeed.config;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * The {@code server} section of the configuration file: the connectors the server opens.
 *
 * The replaced framework owned this section and read far more out of it than the server needs; what is read here is what the server
 * actually opens - the connector the application answers on and the connector the surfaces about the process answer on.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerSettings {

	@JsonProperty
	private List<ConnectorSettings> applicationConnectors = List.of(new ConnectorSettings(8082));

	@JsonProperty
	private List<ConnectorSettings> adminConnectors = List.of(new ConnectorSettings(8083));

	public ConnectorSettings getApplicationConnector() {
		return applicationConnectors.isEmpty() ? null : applicationConnectors.get(0);
	}

	public ConnectorSettings getAdminConnector() {
		return adminConnectors.isEmpty() ? null : adminConnectors.get(0);
	}

	/** One connector: the address and port it binds. */
	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ConnectorSettings {

		@JsonProperty
		private String type = "http";

		@JsonProperty
		private int port;

		@JsonProperty
		private String bindHost;

		public ConnectorSettings() {
		}

		public ConnectorSettings(int port) {
			this.port = port;
		}

	}

}
