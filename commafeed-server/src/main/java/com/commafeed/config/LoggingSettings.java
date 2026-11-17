package com.commafeed.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import lombok.Getter;
import lombok.Setter;

/**
 * The {@code logging} section of the configuration file: the level the server logs at, and the levels of the loggers it names.
 *
 * The replaced framework owned this section and built the whole logging back end from it. The back end is now left as it is configured on
 * the class path, and only the levels this section names are applied, so that a server started with a quiet configuration stays quiet.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoggingSettings {

	@JsonProperty
	private String level = "INFO";

	@JsonProperty
	private Map<String, String> loggers = new LinkedHashMap<>();

	@JsonProperty
	private List<Map<String, Object>> appenders = List.of();

	/** Applies the levels this section names, leaving the appenders as the class path set them. */
	public void apply() {
		if (!(LoggerFactory.getILoggerFactory() instanceof ch.qos.logback.classic.LoggerContext)) {
			return;
		}
		setLevel(Logger.ROOT_LOGGER_NAME, level);
		loggers.forEach(this::setLevel);
	}

	private void setLevel(String name, String value) {
		if (value == null) {
			return;
		}
		org.slf4j.Logger logger = LoggerFactory.getLogger(name);
		if (logger instanceof Logger) {
			((Logger) logger).setLevel(Level.toLevel(value, Level.INFO));
		}
	}

}
