package com.commafeed.frontend.http;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

/**
 * The mapper the server reads and writes JSON entities with.
 *
 * The replaced framework built this mapper and the configuration on it is part of what a client reads: an instant is written as a count of
 * milliseconds rather than of nanoseconds, a field the reader does not know is ignored rather than rejected, and a field whose value is
 * null is still written. Those are kept exactly, because an answer that changed any of them would read differently.
 */
public final class Entities {

	private Entities() {
	}

	public static ObjectMapper newObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new GuavaModule());
		mapper.registerModule(new Jdk8Module());
		mapper.registerModule(new JavaTimeModule());
		mapper.registerModule(new ParameterNamesModule());
		mapper.registerModule(new FuzzyEnumModule());
		mapper.registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false));
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
		mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
		mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
		return mapper;
	}

	/**
	 * Reads an entity into what the resource method takes.
	 *
	 * Three things here are what a client sees rather than how it is written. An entity that is empty or only blank is no entity at all and
	 * the method is handed nothing, which is why such a request reaches the method and fails inside it rather than being refused. The bytes
	 * are turned into characters strictly, so an entity that is not the character set it says it is - or that opens with a byte order mark
	 * - is refused rather than repaired. And anything the reader cannot turn into the method's type is refused with the one message the
	 * replaced reader's mapper wrote for all of it.
	 */
	public static <T> T read(ObjectMapper mapper, byte[] entity, MediaType declared, Class<T> type) {
		String text;
		try {
			text = decode(entity, declared);
		} catch (CharacterCodingException notThatCharset) {
			throw new AnswerException(ErrorAnswers.unreadableJson());
		}
		if (text.isBlank()) {
			return null;
		}
		try {
			return mapper.readValue(text, type);
		} catch (JsonProcessingException unreadable) {
			throw new AnswerException(ErrorAnswers.unreadableJson());
		}
	}

	private static String decode(byte[] entity, MediaType declared) throws CharacterCodingException {
		Charset charset = StandardCharsets.UTF_8;
		String named = declared == null ? null : declared.getParameter("charset");
		if (named != null && !named.isEmpty()) {
			try {
				charset = Charset.forName(named);
			} catch (IllegalArgumentException notACharset) {
				charset = StandardCharsets.UTF_8;
			}
		}
		CharsetDecoder decoder = charset.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		return decoder.decode(ByteBuffer.wrap(entity)).toString();
	}

	/**
	 * The bytes an answer's entity is written as.
	 *
	 * A text is written as it stands whatever the answer says it is, which is how an answer that says it is JSON can carry something that
	 * is not; bytes are written as they stand; everything else is written as JSON.
	 */
	public static byte[] write(ObjectMapper mapper, Object entity) {
		if (entity == null) {
			return new byte[0];
		}
		if (entity instanceof byte[]) {
			return (byte[]) entity;
		}
		if (entity instanceof CharSequence) {
			return entity.toString().getBytes(StandardCharsets.UTF_8);
		}
		try {
			return mapper.writeValueAsBytes(entity);
		} catch (JsonProcessingException notWritable) {
			throw new IllegalStateException(notWritable);
		}
	}

}
