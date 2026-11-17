package com.commafeed.frontend.http;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An answer as it goes out: a status with the reason phrase that belongs to it, the headers in the order they were added, and the entity as
 * bytes.
 *
 * Everything the server decides about an answer is decided on one of these before it is handed to the web server, so that the filters that
 * run after a resource method - the one that marks an answer as not to be cached, and the one that compresses it - work on the same thing
 * the replaced handlers worked on.
 */
public final class Reply {

	private int status;
	private String reason;
	private final Map<String, List<String>> headers = new LinkedHashMap<>();
	private byte[] entity = new byte[0];

	public Reply(int status, String reason) {
		this.status = status;
		this.reason = reason;
	}

	public static Reply of(int status) {
		return new Reply(status, Status.reasonFor(status));
	}

	public int getStatus() {
		return status;
	}

	public String getReason() {
		return reason;
	}

	public Reply status(int code) {
		this.status = code;
		this.reason = Status.reasonFor(code);
		return this;
	}

	public byte[] getEntity() {
		return entity;
	}

	public Reply entity(byte[] bytes) {
		this.entity = bytes == null ? new byte[0] : bytes;
		return this;
	}

	public Reply entity(String text) {
		return entity(text.getBytes(StandardCharsets.UTF_8));
	}

	public Map<String, List<String>> getHeaders() {
		return headers;
	}

	public Reply header(String name, String value) {
		headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
		return this;
	}

	public Reply setHeader(String name, String value) {
		List<String> values = new ArrayList<>();
		values.add(value);
		headers.put(name, values);
		return this;
	}

	public String getHeader(String name) {
		for (Map.Entry<String, List<String>> header : headers.entrySet()) {
			if (header.getKey().equalsIgnoreCase(name) && !header.getValue().isEmpty()) {
				return header.getValue().get(0);
			}
		}
		return null;
	}

	public boolean hasHeader(String name) {
		return getHeader(name) != null;
	}

	public void removeHeader(String name) {
		headers.keySet().removeIf(header -> header.equalsIgnoreCase(name));
	}

	public Reply contentType(String mediaType) {
		return setHeader("Content-Type", mediaType);
	}

	/** Whether the answer carries a content type of the given bare media type. */
	public boolean writes(String bareMediaType) {
		String written = getHeader("Content-Type");
		return written != null && written.toLowerCase(Locale.ROOT).startsWith(bareMediaType.toLowerCase(Locale.ROOT));
	}

}
