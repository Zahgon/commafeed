package com.commafeed.frontend.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a resource method as the one that answers a OPTIONS request on its target. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HttpMethod("OPTIONS")
public @interface OPTIONS {
}
