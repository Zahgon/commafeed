package com.commafeed.frontend.http;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.commafeed.frontend.http.annotation.Consumes;
import com.commafeed.frontend.http.annotation.HttpMethod;
import com.commafeed.frontend.http.annotation.Path;
import com.commafeed.frontend.http.annotation.Produces;

/**
 * One resource method, with everything the dispatcher needs to decide whether a request reaches it: the method it answers, the template of
 * its target, what it reads and what it writes.
 */
public final class Route {

	private final Object resource;
	private final Method method;
	private final String httpMethod;
	private final PathTemplate template;
	private final List<MediaType> consumes;
	private final List<MediaType> produces;

	private Route(Object resource, Method method, String httpMethod, PathTemplate template, List<MediaType> consumes,
			List<MediaType> produces) {
		this.resource = resource;
		this.method = method;
		this.httpMethod = httpMethod;
		this.template = template;
		this.consumes = Collections.unmodifiableList(consumes);
		this.produces = Collections.unmodifiableList(produces);
	}

	/** The routes one resource class declares, or an empty list when it declares none. */
	public static List<Route> of(Object resource) {
		List<Route> routes = new ArrayList<>();
		Class<?> type = resource.getClass();
		Path classPath = type.getAnnotation(Path.class);
		if (classPath == null) {
			return routes;
		}
		List<MediaType> classConsumes = read(type.getAnnotation(Consumes.class));
		List<MediaType> classProduces = written(type.getAnnotation(Produces.class));
		for (Method method : type.getMethods()) {
			String httpMethod = httpMethodOf(method);
			if (httpMethod == null) {
				continue;
			}
			Path methodPath = method.getAnnotation(Path.class);
			String declared = classPath.value() + (methodPath == null ? "" : methodPath.value());
			List<MediaType> consumes = read(method.getAnnotation(Consumes.class));
			List<MediaType> produces = written(method.getAnnotation(Produces.class));
			routes.add(new Route(resource, method, httpMethod, PathTemplate.of(declared), consumes.isEmpty() ? classConsumes : consumes,
					produces.isEmpty() ? classProduces : produces));
		}
		routes.sort((left, right) -> left.method.getName().compareTo(right.method.getName()));
		return routes;
	}

	private static String httpMethodOf(Method method) {
		for (Annotation annotation : method.getAnnotations()) {
			HttpMethod marker = annotation.annotationType().getAnnotation(HttpMethod.class);
			if (marker != null) {
				return marker.value();
			}
		}
		return null;
	}

	private static List<MediaType> read(Consumes annotation) {
		return types(annotation == null ? null : annotation.value());
	}

	private static List<MediaType> written(Produces annotation) {
		return types(annotation == null ? null : annotation.value());
	}

	private static List<MediaType> types(String[] values) {
		List<MediaType> types = new ArrayList<>();
		if (values != null) {
			for (String value : values) {
				types.add(MediaType.of(value));
			}
		}
		return types;
	}

	public boolean claims(String path, Map<String, String> captured) {
		return template.claims(path, captured);
	}

	public boolean claims(String path) {
		return template.claims(path, new java.util.LinkedHashMap<>());
	}

	public Object getResource() {
		return resource;
	}

	public Method getMethod() {
		return method;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	public List<MediaType> getConsumes() {
		return consumes;
	}

	public List<MediaType> getProduces() {
		return produces;
	}

	/**
	 * How closely what this route reads is spelled out, so that two routes on the same target are tried most-specific first - which is how
	 * a reader posting a form reaches the method written for a form rather than the one written for a reader that sends no type at all.
	 */
	public int readSpecificity() {
		int specificity = 0;
		for (MediaType read : consumes) {
			specificity = Math.max(specificity, read.isWildcardType() ? 1 : read.isWildcardSubtype() ? 2 : 3);
		}
		return specificity;
	}

	@Override
	public String toString() {
		return httpMethod + " " + template + " -> " + method.getName();
	}

}
