package com.commafeed.frontend.http;

import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.function.Function;

import com.commafeed.frontend.http.annotation.DefaultValue;
import com.commafeed.frontend.http.annotation.FormDataParam;
import com.commafeed.frontend.http.annotation.HeaderParam;
import com.commafeed.frontend.http.annotation.PathParam;
import com.commafeed.frontend.http.annotation.QueryParam;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Builds the arguments a resource method is called with.
 *
 * The order matters and is the order the parameters are declared in: the replaced framework resolved them one by one and the first one that
 * could not be resolved decided the answer. That is why a request with no credentials to a method whose first parameter is the user is
 * answered that credentials are required, even when the entity it carries would not have read either.
 */
public final class Arguments {

	/** What a provider of its own answers for a parameter it does not provide. */
	public static final Object NONE = new Object();

	private final ObjectMapper mapper;
	private final Function<Parameter, Object> provided;

	/**
	 * @param provided
	 *            answers a parameter the request itself does not carry - the authenticated user, the session and what the request was asked
	 *            on - or {@link #NONE}
	 */
	public Arguments(ObjectMapper mapper, Function<Parameter, Object> provided) {
		this.mapper = mapper;
		this.provided = provided;
	}

	public Object[] bind(Route route, Request request, Map<String, String> captured, MediaType declared) {
		Parameter[] parameters = route.getMethod().getParameters();
		Object[] arguments = new Object[parameters.length];
		for (int at = 0; at < parameters.length; at++) {
			arguments[at] = bindOne(parameters[at], request, captured, declared);
		}
		return arguments;
	}

	private Object bindOne(Parameter parameter, Request request, Map<String, String> captured, MediaType declared) {
		Object given = provided.apply(parameter);
		if (given != NONE) {
			return given;
		}
		Class<?> type = parameter.getType();
		PathParam path = parameter.getAnnotation(PathParam.class);
		if (path != null) {
			String raw = captured.get(path.value());
			return Conversion.convert(type, raw == null ? null : RequestTarget.decode(raw, false), declaredDefault(parameter), "path param",
					path.value());
		}
		QueryParam query = parameter.getAnnotation(QueryParam.class);
		if (query != null) {
			return Conversion.convert(type, request.getTarget().getQueryParameter(query.value()), declaredDefault(parameter), "query param",
					query.value());
		}
		HeaderParam header = parameter.getAnnotation(HeaderParam.class);
		if (header != null) {
			return Conversion.convert(type, request.getHeader(header.value()), declaredDefault(parameter), "header param", header.value());
		}
		FormDataParam part = parameter.getAnnotation(FormDataParam.class);
		if (part != null) {
			unlessUndeclared(declared);
			return FormData.multipart(request.getEntity(), declared).openStream(part.value());
		}
		if (type == FormData.class) {
			return form(request, declared);
		}
		return Entities.read(mapper, request.getEntity(), unlessUndeclared(declared), type);
	}

	private static FormData form(Request request, MediaType declared) {
		unlessUndeclared(declared);
		if ("multipart".equals(declared.getType())) {
			return FormData.multipart(request.getEntity(), declared);
		}
		return FormData.urlencoded(request.getEntity());
	}

	/**
	 * Refuses an entity whose type the request did not say.
	 *
	 * The replaced runtime looked for a reader by the type the request declared, and an entity that declares none was read as bytes, which
	 * nothing here reads: such a request was refused where its entity would have been read, not where its method was chosen.
	 */
	private static MediaType unlessUndeclared(MediaType declared) {
		if (declared == null) {
			throw new AnswerException(ErrorAnswers.framework(415));
		}
		return declared;
	}

	private static String declaredDefault(Parameter parameter) {
		DefaultValue annotation = parameter.getAnnotation(DefaultValue.class);
		return annotation == null ? null : annotation.value();
	}

}
