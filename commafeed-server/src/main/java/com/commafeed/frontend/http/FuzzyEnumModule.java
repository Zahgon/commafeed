package com.commafeed.frontend.http;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Reads an enum inside a JSON entity the same forgiving way a query parameter is read.
 *
 * The replaced framework registered a module of this shape on the mapper it built, so a request body naming a constant in another letter
 * case, or with a hyphen where the constant has an underscore, was accepted. Keeping it keeps those bodies accepted.
 */
public class FuzzyEnumModule extends SimpleModule {

	private static final long serialVersionUID = 1L;

	public FuzzyEnumModule() {
		setDeserializerModifier(new BeanDeserializerModifier() {

			private static final long serialVersionUID = 1L;

			@Override
			public JsonDeserializer<?> modifyEnumDeserializer(DeserializationConfig config, JavaType type, BeanDescription description,
					JsonDeserializer<?> deserializer) {
				return new FuzzyEnumDeserializer<>(type.getRawClass());
			}

		});
	}

	/** Reads one enum value out of a JSON string. */
	private static final class FuzzyEnumDeserializer<T extends Enum<T>> extends JsonDeserializer<T> {

		private final Class<T> type;

		@SuppressWarnings("unchecked")
		FuzzyEnumDeserializer(Class<?> type) {
			this.type = (Class<T>) type;
		}

		@Override
		public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
			String text = parser.getValueAsString();
			if (text == null) {
				return null;
			}
			T found = Enums.fromStringFuzzy(text, type.getEnumConstants());
			if (found == null) {
				throw JsonMappingException.from(parser, type.getName() + " has no constant named " + text);
			}
			return found;
		}

	}

}
