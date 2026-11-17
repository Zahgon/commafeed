package com.commafeed.frontend.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fields of an entity a client sent as a form, either urlencoded or in parts.
 *
 * Both shapes reach the same resource methods, so both are read into the same thing: a field's values in the order they arrived, and for a
 * part also the bytes it carried, which is what the OPML import reads.
 */
public final class FormData {

	private final Map<String, List<String>> values = new LinkedHashMap<>();
	private final Map<String, byte[]> contents = new LinkedHashMap<>();

	private FormData() {
	}

	/** The fields of an urlencoded entity, decoded with {@code +} standing for a space. */
	public static FormData urlencoded(byte[] entity) {
		FormData form = new FormData();
		String text = new String(entity, StandardCharsets.UTF_8);
		for (String pair : text.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			int equals = pair.indexOf('=');
			String name = RequestTarget.decode(equals < 0 ? pair : pair.substring(0, equals), true);
			String value = equals < 0 ? "" : RequestTarget.decode(pair.substring(equals + 1), true);
			form.add(name, value, value.getBytes(StandardCharsets.UTF_8));
		}
		return form;
	}

	/**
	 * The parts of a multipart entity, split on the boundary the content type names.
	 *
	 * An entity whose content type names no boundary is not one the reader can read at all, and the replaced reader answered that the
	 * request was bad rather than handing the method nothing.
	 */
	public static FormData multipart(byte[] entity, MediaType declared) {
		FormData form = new FormData();
		String boundary = declared == null ? null : declared.getParameter("boundary");
		if (boundary == null || boundary.isEmpty()) {
			throw new AnswerException(ErrorAnswers.framework(400));
		}
		byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
		List<Integer> starts = new ArrayList<>();
		for (int at = 0; at + delimiter.length <= entity.length; at++) {
			if (matches(entity, at, delimiter)) {
				starts.add(at);
			}
		}
		for (int part = 0; part + 1 < starts.size(); part++) {
			int from = starts.get(part) + delimiter.length;
			int to = starts.get(part + 1);
			form.addPart(entity, from, to);
		}
		return form;
	}

	private static boolean matches(byte[] entity, int at, byte[] delimiter) {
		for (int index = 0; index < delimiter.length; index++) {
			if (entity[at + index] != delimiter[index]) {
				return false;
			}
		}
		return true;
	}

	/** Reads one part: its headers, then the bytes between them and the next boundary. */
	private void addPart(byte[] entity, int from, int to) {
		int blank = indexOfBlankLine(entity, from, to);
		if (blank < 0) {
			return;
		}
		String headers = new String(entity, from, blank - from, StandardCharsets.UTF_8);
		String name = null;
		for (String header : headers.split("\r\n")) {
			int disposition = header.toLowerCase(java.util.Locale.ROOT).indexOf("name=\"");
			if (disposition >= 0) {
				int end = header.indexOf('"', disposition + 6);
				name = end < 0 ? null : header.substring(disposition + 6, end);
			}
		}
		if (name == null) {
			return;
		}
		int start = blank + 4;
		int end = to;
		// the bytes a part carries end before the line break that precedes the next boundary
		if (end - 2 >= start && entity[end - 2] == '\r' && entity[end - 1] == '\n') {
			end -= 2;
		}
		byte[] content = java.util.Arrays.copyOfRange(entity, Math.min(start, end), Math.max(start, end));
		add(name, new String(content, StandardCharsets.UTF_8), content);
	}

	private static int indexOfBlankLine(byte[] entity, int from, int to) {
		for (int at = from; at + 3 < to; at++) {
			if (entity[at] == '\r' && entity[at + 1] == '\n' && entity[at + 2] == '\r' && entity[at + 3] == '\n') {
				return at;
			}
		}
		return -1;
	}

	private void add(String name, String value, byte[] content) {
		values.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
		contents.putIfAbsent(name, content);
	}

	/** Every field, with the values it carries in the order they arrived. */
	public Map<String, List<String>> getFields() {
		return Collections.unmodifiableMap(values);
	}

	/** The first value a field carries, or null when it carries none. */
	public String getFirst(String name) {
		List<String> found = values.get(name);
		return found == null || found.isEmpty() ? null : found.get(0);
	}

	/** The bytes a field carried, as a stream, or null when the entity has no such field. */
	public InputStream openStream(String name) {
		byte[] content = contents.get(name);
		return content == null ? null : new ByteArrayInputStream(content);
	}

}
