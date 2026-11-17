package com.commafeed.backend.favicon;

import com.commafeed.frontend.http.MediaType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Getter
@Slf4j
public class Favicon {

	private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.of("image/x-icon");

	private final byte[] icon;
	private final MediaType mediaType;

	public Favicon(byte[] icon, String contentType) {
		this(icon, parseMediaType(contentType));
	}

	private static MediaType parseMediaType(String contentType) {
		try {
			return MediaType.of(contentType);
		} catch (Exception e) {
			log.debug("invalid content type '{}' received, returning default value", contentType);
			return DEFAULT_MEDIA_TYPE;
		}
	}
}