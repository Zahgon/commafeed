package com.commafeed.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.commafeed.CommaFeedConfiguration;
import com.commafeed.frontend.http.FuzzyEnumModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Reads the configuration file the server is started with.
 *
 * The replaced framework read it, and three things it did are part of how the server is configured, so all three are kept: a
 * {@code ${NAME}} in the file is replaced with the environment variable of that name, an environment variable named
 * {@code CF_<SECTION>_<FIELD>} overrides the field it names, and a field name is matched without regard to case because an environment
 * variable cannot carry one.
 */
public final class ConfigurationLoader {

	/** The prefix an environment variable carries to override a field of the configuration. */
	public static final String OVERRIDE_PREFIX = "CF_";

	private static final Pattern REFERENCE = Pattern.compile("\\$\\{([^}]+)}");

	private ConfigurationLoader() {
	}

	public static CommaFeedConfiguration load(Path file) throws IOException {
		return load(file, System.getenv());
	}

	static CommaFeedConfiguration load(Path file, Map<String, String> environment) throws IOException {
		String text = substitute(Files.readString(file, StandardCharsets.UTF_8), environment);
		ObjectMapper mapper = newConfigurationMapper();
		JsonNode read = mapper.readTree(text);
		ObjectNode root = read.isObject() ? (ObjectNode) read : mapper.createObjectNode();
		applyOverrides(root, environment);
		CommaFeedConfiguration configuration = mapper.treeToValue(root, CommaFeedConfiguration.class);
		// the levels first, so that checking the rest of the configuration is as quiet as asked for
		configuration.getLoggingSettings().apply();
		validate(configuration);
		return configuration;
	}

	private static ObjectMapper newConfigurationMapper() {
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		// an environment variable cannot carry the case of the field it overrides
		mapper.setConfig(mapper.getDeserializationConfig().with(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
		// nor the case of a value, and the file itself names them in lower case
		mapper.registerModule(new FuzzyEnumModule());
		return mapper;
	}

	/** Replaces every {@code ${NAME}} with the environment variable of that name, if it is set. */
	static String substitute(String text, Map<String, String> environment) {
		Matcher reference = REFERENCE.matcher(text);
		StringBuilder replaced = new StringBuilder();
		while (reference.find()) {
			String value = environment.get(reference.group(1));
			reference.appendReplacement(replaced, Matcher.quoteReplacement(value != null ? value : reference.group()));
		}
		reference.appendTail(replaced);
		return replaced.toString();
	}

	/**
	 * Applies every {@code CF_<SECTION>_<FIELD>} variable onto the tree the file was read into.
	 *
	 * The variable's name is split on the underscores and each part names one step down the tree, so {@code CF_APP_ALLOWREGISTRATIONS} sets
	 * {@code app.allowRegistrations}. A step that names a field the file already carries keeps the file's spelling of it.
	 */
	static void applyOverrides(ObjectNode root, Map<String, String> environment) {
		Map<String, String> overrides = new TreeMap<>(environment.entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith(OVERRIDE_PREFIX))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
		for (Map.Entry<String, String> override : overrides.entrySet()) {
			String[] steps = override.getKey().substring(OVERRIDE_PREFIX.length()).toLowerCase(Locale.ROOT).split("_");
			ObjectNode node = root;
			for (int step = 0; step < steps.length - 1 && node != null; step++) {
				node = descend(node, steps[step]);
			}
			if (node != null && steps.length > 0) {
				node.put(fieldName(node, steps[steps.length - 1]), override.getValue());
			}
		}
	}

	private static ObjectNode descend(ObjectNode node, String step) {
		JsonNode child = node.get(fieldName(node, step));
		if (child == null || child.isNull()) {
			return node.putObject(step);
		}
		return child.isObject() ? (ObjectNode) child : null;
	}

	/** The name the tree already uses for a step, or the step itself when it carries none. */
	private static String fieldName(ObjectNode node, String step) {
		for (Iterator<String> names = node.fieldNames(); names.hasNext();) {
			String name = names.next();
			if (name.toLowerCase(Locale.ROOT).equals(step)) {
				return name;
			}
		}
		return step;
	}

	private static void validate(CommaFeedConfiguration configuration) {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		Set<ConstraintViolation<CommaFeedConfiguration>> violations = validator.validate(configuration);
		if (!violations.isEmpty()) {
			String message = violations.stream()
					.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
					.sorted()
					.collect(Collectors.joining(", "));
			throw new IllegalArgumentException("the configuration is not valid: " + message);
		}
	}

}
