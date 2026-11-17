package com.commafeed.frontend.http;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the target of a request the way the replaced container read it.
 *
 * The container refused some spellings of a target outright, and for the rest it worked out the target a handler is chosen by - which is
 * not the target it arrived as. Three things follow and a client sees all of them:
 *
 * <ul>
 * <li>a target carrying a zero byte, an empty segment between two slashes, or a dot segment written percent-encoded, is refused with the
 * container's own page and no message on it;</li>
 * <li>a dot segment written plainly is worked out, so a target that steps down and back up again reaches what the target without the step
 * reaches - and a target that steps above the root is refused;</li>
 * <li>the target a handler is chosen by has its matrix parameters cut off and its percent escapes worked out, while the API layer is handed
 * the target as it arrived, which is why a percent escape there does not reach what the character it stands for reaches.</li>
 * </ul>
 */
public final class Targets {

	private Targets() {
	}

	/** Whether the container refused the spelling of this target before looking at anything else. */
	public static boolean refused(String rawPath) {
		if (rawPath == null || rawPath.isEmpty()) {
			return false;
		}
		List<String> segments = RequestTarget.of(rawPath, null).getSegments();
		int depth = 0;
		for (int at = 0; at < segments.size(); at++) {
			String segment = segments.get(at);
			if (segment.isEmpty()) {
				if (at < segments.size() - 1) {
					return true;
				}
				continue;
			}
			String decoded = RequestTarget.decode(segment, false);
			if (decoded.indexOf('\0') >= 0) {
				return true;
			}
			if ((".".equals(decoded) || "..".equals(decoded)) && !decoded.equals(segment)) {
				return true;
			}
			if ("..".equals(segment)) {
				if (depth == 0) {
					return true;
				}
				depth--;
			} else if (!".".equals(segment)) {
				depth++;
			}
		}
		return false;
	}

	/** The target a handler is chosen by: matrix parameters cut off, dot segments worked out. */
	public static String canonical(String rawPath) {
		RequestTarget target = RequestTarget.of(rawPath, null);
		List<String> kept = new ArrayList<>();
		boolean trailing = false;
		for (String segment : target.getSegments()) {
			if (".".equals(segment)) {
				trailing = false;
				continue;
			}
			if ("..".equals(segment)) {
				if (!kept.isEmpty()) {
					kept.remove(kept.size() - 1);
				}
				trailing = false;
				continue;
			}
			if (segment.isEmpty()) {
				trailing = true;
				continue;
			}
			kept.add(RequestTarget.decode(segment, false));
			trailing = false;
		}
		return "/" + String.join("/", kept) + (trailing && !kept.isEmpty() ? "/" : "");
	}

}
