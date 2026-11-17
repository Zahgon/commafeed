package com.commafeed.frontend.http;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Checks an entity against the constraints its class declares, and writes what the replaced framework wrote when one of them was broken.
 *
 * Two things are behaviour rather than detail: the status is the one for an entity that reads but is not acceptable, not a plain bad
 * request, and each message is the name of the field followed by the text the validator produced, which is the validator's own text and not
 * one of ours.
 */
public final class Violations {

	private Violations() {
	}

	/** The messages for everything broken about the entity, or an empty list when nothing is. */
	public static List<String> of(Validator validator, Object entity) {
		if (entity == null) {
			return List.of();
		}
		Set<ConstraintViolation<Object>> violations = validator.validate(entity);
		return violations.stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.collect(Collectors.toList());
	}

}
