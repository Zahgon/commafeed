package com.commafeed.backend.dao;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Reads the entity a data access class is declared for off its own type parameter, which is how the replaced framework's base class found
 * it.
 */
final class Generics {

	private Generics() {
	}

	static Class<?> entityTypeOf(Class<?> type) {
		for (Class<?> walked = type; walked != null; walked = walked.getSuperclass()) {
			Type superclass = walked.getGenericSuperclass();
			if (superclass instanceof ParameterizedType) {
				Type[] arguments = ((ParameterizedType) superclass).getActualTypeArguments();
				if (arguments.length > 0 && arguments[0] instanceof Class) {
					return (Class<?>) arguments[0];
				}
			}
		}
		throw new IllegalStateException("no entity type declared by " + type.getName());
	}

}
