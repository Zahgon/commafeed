package com.commafeed.frontend.http;

import java.util.List;
import java.util.Map;

/**
 * What a resource method is told about the target it was reached on.
 *
 * The replaced framework handed the Fever-compatible surface an object of this shape so that it could read the parameters of the query as
 * well as of the entity; only the part of it that was used is here.
 */
public final class UriInformation {

	private final RequestTarget target;

	public UriInformation(RequestTarget target) {
		this.target = target;
	}

	public Map<String, List<String>> getQueryParameters() {
		return target.getQueryParameters();
	}

	public String getPath() {
		return target.getRawPath();
	}

}
