package com.commafeed.frontend.http;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a resource method answers: a status, the headers it adds and the object that becomes the entity.
 *
 * This stands in for the response type the replaced runtime offered, with the same small surface the resource classes used. The entity is
 * kept as the object the method returned; what it is written as is decided later, from the media type the request negotiated, exactly as
 * the replaced runtime decided it - and a type the method names itself wins over the negotiated one.
 */
public final class Answer {

	private final int status;
	private final Object entity;
	private final Map<String, List<String>> headers;

	private Answer(int status, Object entity, Map<String, List<String>> headers) {
		this.status = status;
		this.entity = entity;
		this.headers = Collections.unmodifiableMap(headers);
	}

	public static Builder status(int status) {
		return new Builder(status);
	}

	public static Builder status(Status status) {
		return new Builder(status.getCode());
	}

	public static Builder ok() {
		return new Builder(200);
	}

	public static Builder ok(Object entity) {
		return new Builder(200).entity(entity);
	}

	public static Builder ok(Object entity, String mediaType) {
		return new Builder(200).entity(entity).type(mediaType);
	}

	/** An answer with the given entity, for a method that returns the entity and nothing else. */
	public static Answer of(Object entity) {
		return new Builder(200).entity(entity).build();
	}

	/** The redirect the replaced runtime built for a temporary location: 307, with the location. */
	public static Builder temporaryRedirect(URI location) {
		return new Builder(307).header("Location", location.toString());
	}

	public int getStatus() {
		return status;
	}

	public Object getEntity() {
		return entity;
	}

	public Map<String, List<String>> getHeaders() {
		return headers;
	}

	/** Builds an answer the way the replaced runtime's builder did, in the same call order. */
	public static final class Builder {

		private final int status;
		private final Map<String, List<String>> headers = new LinkedHashMap<>();
		private Object entity;

		private Builder(int status) {
			this.status = status;
		}

		public Builder entity(Object value) {
			this.entity = value;
			return this;
		}

		public Builder header(String name, String value) {
			headers.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
			return this;
		}

		public Builder type(String mediaType) {
			return header("Content-Type", mediaType);
		}

		public Builder cacheControl(CacheControl cacheControl) {
			return header("Cache-Control", cacheControl.toString());
		}

		public Builder expires(Date when) {
			return header("Expires", Http.formatDate(when.toInstant()));
		}

		public Builder lastModified(Date when) {
			return header("Last-Modified", Http.formatDate(when.toInstant()));
		}

		public Builder lastModified(Instant when) {
			return header("Last-Modified", Http.formatDate(when));
		}

		public Answer build() {
			return new Answer(status, entity, headers);
		}

	}

}
