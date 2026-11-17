package com.commafeed.frontend.http;

import java.util.Locale;
import java.util.Map;

/**
 * What a file name says a file is.
 *
 * The replaced container had a table of these and two parts of the server read it: the handler that compresses an answer, which stepped
 * aside for a target whose name says it is already compressed, and the servlet that serves the web client's files, which said what it was
 * serving from the same table - and said that anything not in the table was a document to look at, which is why the OpenAPI document
 * written as YAML is served as one.
 */
public final class MimeTypes {

	/** What a file with no extension in the table is served as. */
	public static final String DEFAULT = "text/html";

	/** What the replaced container's table said, for the extensions the server serves. */
	private static final Map<String, String> BY_EXTENSION = Map.ofEntries(Map.entry("html", "text/html"), Map.entry("htm", "text/html"),
			Map.entry("css", "text/css"), Map.entry("js", "application/javascript"), Map.entry("mjs", "application/javascript"),
			Map.entry("json", "application/json"), Map.entry("txt", "text/plain"), Map.entry("xml", "text/xml"),
			Map.entry("svg", "image/svg+xml"), Map.entry("png", "image/png"), Map.entry("gif", "image/gif"), Map.entry("jpg", "image/jpeg"),
			Map.entry("jpeg", "image/jpeg"), Map.entry("ico", "image/x-icon"), Map.entry("webp", "image/webp"),
			Map.entry("woff", "font/woff"), Map.entry("woff2", "font/woff2"), Map.entry("ttf", "font/ttf"),
			Map.entry("eot", "application/vnd.ms-fontobject"), Map.entry("map", "application/json"), Map.entry("wasm", "application/wasm"),
			Map.entry("zip", "application/zip"), Map.entry("gz", "application/gzip"));

	private MimeTypes() {
	}

	/** What the table says the target is, or null when the table says nothing about it. */
	public static String byExtension(String path) {
		int slash = path.lastIndexOf('/');
		String name = slash < 0 ? path : path.substring(slash + 1);
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return null;
		}
		return BY_EXTENSION.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
	}

}
