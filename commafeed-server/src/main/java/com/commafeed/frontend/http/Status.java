package com.commafeed.frontend.http;

/**
 * The status codes and reason phrases the server answers with.
 *
 * The reason phrase is part of what a client reads, and the replaced container wrote its own for each code, so they are kept here rather
 * than left to whatever the new server would write.
 */
public enum Status {

	OK(200, "OK"),
	CREATED(201, "Created"),
	NO_CONTENT(204, "No Content"),
	PARTIAL_CONTENT(206, "Partial Content"),
	MOVED_TEMPORARILY(302, "Found"),
	NOT_MODIFIED(304, "Not Modified"),
	TEMPORARY_REDIRECT(307, "Temporary Redirect"),
	BAD_REQUEST(400, "Bad Request"),
	UNAUTHORIZED(401, "Unauthorized"),
	FORBIDDEN(403, "Forbidden"),
	CONFLICT(409, "Conflict"),
	NOT_FOUND(404, "Not Found"),
	METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
	NOT_ACCEPTABLE(406, "Not Acceptable"),
	UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
	UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
	NOT_IMPLEMENTED(501, "Not Implemented"),
	SERVICE_UNAVAILABLE(503, "Service Unavailable"),
	INTERNAL_SERVER_ERROR(500, "Server Error");

	private final int code;
	private final String reason;

	Status(int code, String reason) {
		this.code = code;
		this.reason = reason;
	}

	public int getCode() {
		return code;
	}

	public String getReason() {
		return reason;
	}

	/** The reason phrase for a code, or null when the server has none of its own for it. */
	public static String reasonFor(int code) {
		for (Status status : values()) {
			if (status.code == code) {
				return status.reason;
			}
		}
		return null;
	}

}
