package com.commafeed.frontend.http;

import java.security.SecureRandom;
import java.util.Collection;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The answers the server writes for a request that never reaches a resource method, or whose resource method failed.
 *
 * The replaced stack had two different writers for these, and which one answered depended on how far the request got, so both are
 * reproduced here:
 *
 * <ul>
 * <li>the JSON document below, written by the mappers the replaced framework registered, for a request that reached the dispatching
 * layer;</li>
 * <li>nothing at all, for a status the container answered by itself before the dispatching layer saw the request - the container then
 * filled in its own page, which is why those answers carry neither an entity nor a content type here.</li>
 * </ul>
 */
public final class ErrorAnswers {

	private static final SecureRandom RANDOM = new SecureRandom();

	private ErrorAnswers() {
	}

	/** The document the replaced mappers wrote, with a message of the caller's own. */
	public static ErrorMessage message(int code, String text) {
		return new ErrorMessage(code, text);
	}

	/**
	 * The answer for a status the dispatching layer decided on, written as the replaced mappers wrote it: {@code HTTP <code> <reason>} as
	 * the message.
	 */
	public static Answer framework(int code) {
		String message = "HTTP " + code + " " + Status.reasonFor(code);
		return Answer.status(code).type(MediaType.APPLICATION_JSON).entity(new ErrorMessage(code, message)).build();
	}

	/** The answer for an entity the JSON reader could not read. */
	public static Answer unreadableJson() {
		return Answer.status(400).type(MediaType.APPLICATION_JSON).entity(new ErrorMessage(400, "Unable to process JSON")).build();
	}

	/**
	 * The answer for a failure the server logged: the message names the identifier it logged it under, which is a fresh random one per
	 * failure.
	 */
	public static Answer logged(long id) {
		String message = String.format("There was an error processing your request. It has been logged (ID %016x).", id);
		return Answer.status(500).type(MediaType.APPLICATION_JSON).entity(new ErrorMessage(500, message)).build();
	}

	public static long nextLogId() {
		return RANDOM.nextLong();
	}

	/** The answer for an entity the validator rejected. */
	public static Answer invalidEntity(Collection<String> messages) {
		return Answer.status(422).type(MediaType.APPLICATION_JSON).entity(new ValidationErrors(messages)).build();
	}

	/**
	 * The answer for a status the container decided on before the dispatching layer saw the request: a status and no entity of the server's
	 * own.
	 */
	public static Answer container(int code) {
		return Answer.status(code).build();
	}

	/** The document shape the replaced framework's mappers wrote: a code and a message. */
	public static final class ErrorMessage {

		private final int code;
		private final String message;

		ErrorMessage(int code, String message) {
			this.code = code;
			this.message = message;
		}

		@JsonProperty
		public int getCode() {
			return code;
		}

		@JsonProperty
		public String getMessage() {
			return message;
		}

	}

	/** The document shape the replaced framework's validation mapper wrote: the messages alone. */
	public static final class ValidationErrors {

		private final Collection<String> errors;

		ValidationErrors(Collection<String> errors) {
			this.errors = errors;
		}

		@JsonProperty
		public Collection<String> getErrors() {
			return errors;
		}

	}

}
