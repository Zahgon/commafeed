package com.commafeed.frontend.http;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Timed;
import com.commafeed.backend.dao.UnitOfWork;
import com.commafeed.frontend.auth.SecurityCheck;
import com.commafeed.frontend.auth.SecurityChecks;
import com.commafeed.frontend.http.annotation.Context;
import com.commafeed.frontend.session.SessionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import jakarta.validation.Validator;

/**
 * Routes a request under the API prefix to a resource method and writes what it answers.
 *
 * This is the part the replaced runtime did, and the order it did it in is visible to a client, so the order here is the same one:
 *
 * <ol>
 * <li>no route claims the target, and the answer is that there is no such target;</li>
 * <li>the target is asked what it allows, and the answer lists it;</li>
 * <li>no route on the target answers the method, and the answer says which ones do;</li>
 * <li>the entity declares a type no route on the target reads, and the answer refuses it;</li>
 * <li>the client accepts nothing the route writes, and the answer says so;</li>
 * <li>the arguments are built, in the order the method declares them, and the first one that cannot be built decides the answer;</li>
 * <li>what the entity read is checked against its constraints;</li>
 * <li>the method runs inside a unit of work, and what it answers is written.</li>
 * </ol>
 */
public final class RestDispatcher {

	/** Where this dispatcher is reached, which is also what the replaced servlet was mapped on. */
	public static final String PREFIX = "/rest";

	private static final Logger log = LoggerFactory.getLogger(RestDispatcher.class);

	/** The methods the dispatcher is given at all; the container answered the rest by itself. */
	private static final Set<String> DISPATCHED = Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");

	private final List<Route> routes = new ArrayList<>();
	private final ObjectMapper mapper;
	private final Validator validator;
	private final UnitOfWork unitOfWork;
	private final SecurityChecks securityChecks;
	private final SessionStore sessions;
	private final MetricRegistry metrics;

	public RestDispatcher(ObjectMapper mapper, Validator validator, UnitOfWork unitOfWork, SecurityChecks securityChecks,
			SessionStore sessions, MetricRegistry metrics) {
		this.mapper = mapper;
		this.validator = validator;
		this.unitOfWork = unitOfWork;
		this.securityChecks = securityChecks;
		this.sessions = sessions;
		this.metrics = metrics;
	}

	public void register(Object resource) {
		routes.addAll(Route.of(resource));
	}

	public static boolean dispatches(String method) {
		return DISPATCHED.contains(method);
	}

	/**
	 * Answers the request, which is already known to be under the prefix.
	 *
	 * The route templates are written below the prefix the dispatching layer was reached under, and they are matched against the target as
	 * it arrived rather than against the target a handler was chosen by: a percent escape in it is not worked out first, and neither is a
	 * dot segment.
	 */
	public Reply dispatch(Request request) {
		String arrived = request.getTarget().getMatchedPath();
		String path = arrived.startsWith(PREFIX) ? arrived.substring(PREFIX.length()) : arrived;
		List<Route> claiming = new ArrayList<>();
		for (Route route : routes) {
			if (route.claims(path)) {
				claiming.add(route);
			}
		}
		if (claiming.isEmpty()) {
			return written(request, ErrorAnswers.framework(404), null);
		}
		if ("OPTIONS".equals(request.getMethod())) {
			return allowed(claiming);
		}
		String wanted = "HEAD".equals(request.getMethod()) ? "GET" : request.getMethod();
		List<Route> answering = new ArrayList<>();
		for (Route route : claiming) {
			if (route.getHttpMethod().equals(wanted)) {
				answering.add(route);
			}
		}
		if (answering.isEmpty()) {
			Set<String> methods = new HashSet<>(declared(claiming));
			methods.add("OPTIONS");
			return written(request, ErrorAnswers.framework(405), null).setHeader("Allow", String.join(",", methods));
		}
		Declared entity;
		try {
			entity = declaredType(request);
		} catch (AnswerException refused) {
			return written(request, refused.getAnswer(), null);
		}
		MediaType declared = entity.type;
		List<Route> reading = new ArrayList<>();
		for (Route route : answering) {
			if (declared == null || Negotiation.reads(route.getConsumes(), declared)) {
				reading.add(route);
			}
		}
		if (reading.isEmpty()) {
			return written(request, ErrorAnswers.framework(415), null);
		}
		reading.sort(Comparator.comparingInt(Route::readSpecificity).reversed());
		List<Negotiation.Offer> offers;
		try {
			offers = Negotiation.offers(request);
		} catch (Negotiation.BadOffer badOffer) {
			return ContainerAnswers.errorPage(request, 400, "Bad Request");
		}
		for (Route route : reading) {
			MediaType writes = Negotiation.choose(route.getProduces(), offers);
			if (writes != null) {
				return invoke(request, route, path, declared, writes);
			}
		}
		return written(request, ErrorAnswers.framework(406), null);
	}

	/** What the target allows, as the replaced runtime wrote it: the header without the spaces. */
	private Reply allowed(List<Route> claiming) {
		Set<String> methods = new HashSet<>(declared(claiming));
		methods.add("OPTIONS");
		if (methods.contains("GET")) {
			methods.add("HEAD");
		}
		return Reply.of(200)
				.setHeader("Allow", String.join(",", methods))
				.contentType(MediaType.TEXT_PLAIN)
				.entity(String.join(", ", methods));
	}

	private static Set<String> declared(List<Route> claiming) {
		Set<String> methods = new HashSet<>();
		for (Route route : claiming) {
			methods.add(route.getHttpMethod());
		}
		return methods;
	}

	/**
	 * What the request says its entity is, or nothing when it says nothing.
	 *
	 * A request that says nothing is not held to what the methods on its target read: that is why a plain read of a target whose methods
	 * read only JSON is answered rather than refused, and it is also why a post with an entity but no type reaches the method and is then
	 * refused where the entity would have been read, because there is no reader for an entity of no type.
	 *
	 * A request that says it more than once, or says something that is not a media type, never reached the dispatching layer: the container
	 * refused it and wrote its own page.
	 */
	private Declared declaredType(Request request) {
		List<String> values = request.getHeaders("content-type");
		if (values.size() > 1) {
			throw new AnswerException(Answer.status(400).build());
		}
		if (values.isEmpty()) {
			return new Declared(null);
		}
		MediaType declared = MediaType.parse(values.get(0));
		if (declared == null) {
			throw new AnswerException(Answer.status(400).build());
		}
		return new Declared(declared);
	}

	/**
	 * Calls the method and writes what it answers.
	 *
	 * Everything from here on happens inside one unit of work, the arguments included: the replaced framework opened the session before it
	 * resolved them, which is what lets the check for the user talk to the database, and it is also why a request that is refused there has
	 * already opened - and rolled back - a transaction.
	 */
	private Reply invoke(Request request, Route route, String path, MediaType declared, MediaType writes) {
		Map<String, String> captured = new LinkedHashMap<>();
		route.claims(path, captured);
		Timer.Context timed = timing(route);
		try {
			return written(request, unitOfWork.call(() -> {
				Object[] arguments = new Arguments(mapper, provider(request, route)).bind(route, request, captured, declared);
				List<String> broken = brokenConstraints(route, arguments);
				if (!broken.isEmpty()) {
					return ErrorAnswers.invalidEntity(broken);
				}
				Object answered = call(route, arguments);
				return answered instanceof Answer ? (Answer) answered : Answer.of(answered);
			}), writes);
		} catch (AnswerException answered) {
			return written(request, answered.getAnswer(), writes);
		} catch (Conversion.NotConvertible notConvertible) {
			return written(request, notConvertible.getAnswer(), writes);
		} catch (RuntimeException failed) {
			long id = ErrorAnswers.nextLogId();
			log.error(String.format("%016x", id), failed);
			return written(request, ErrorAnswers.logged(id), writes);
		} finally {
			if (timed != null) {
				timed.stop();
			}
		}
	}

	/**
	 * Measures how long a method marked as measured takes.
	 *
	 * The replaced framework read that mark and put a measurement under the method's own name into the registry the second connector
	 * answers with; the marks are still read, here.
	 */
	private Timer.Context timing(Route route) {
		if (route.getMethod().getAnnotation(Timed.class) == null) {
			return null;
		}
		return metrics.timer(MetricRegistry.name(route.getMethod().getDeclaringClass(), route.getMethod().getName())).time();
	}

	private static Object call(Route route, Object[] arguments) {
		try {
			return route.getMethod().invoke(route.getResource(), arguments);
		} catch (InvocationTargetException failed) {
			Throwable cause = failed.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException(cause);
		} catch (ReflectiveOperationException broken) {
			throw new IllegalStateException(broken);
		}
	}

	private List<String> brokenConstraints(Route route, Object[] arguments) {
		List<String> broken = new ArrayList<>();
		Parameter[] parameters = route.getMethod().getParameters();
		for (int at = 0; at < parameters.length; at++) {
			if (parameters[at].getAnnotation(Valid.class) != null) {
				broken.addAll(Violations.of(validator, arguments[at]));
			}
		}
		return broken;
	}

	/** What the request itself provides for a parameter: the user, the session, the target. */
	private Function<Parameter, Object> provider(Request request, Route route) {
		return parameter -> {
			SecurityCheck check = parameter.getAnnotation(SecurityCheck.class);
			if (check != null) {
				return securityChecks.check(request, check);
			}
			boolean contextual = parameter.getAnnotation(Context.class) != null;
			if (parameter.getType() == SessionHelper.class) {
				return new SessionHelper(request, sessions);
			}
			if (parameter.getType() == UriInformation.class) {
				return new UriInformation(request.getTarget());
			}
			return contextual ? null : Arguments.NONE;
		};
	}

	/**
	 * Writes an answer: its status, the headers it carries, and its entity as the negotiated type.
	 *
	 * A type the answer names itself wins over the negotiated one, which is how an answer carrying an icon says it is an icon although the
	 * method is declared as writing JSON. An answer with no entity carries no content type at all, and when its status is one of the
	 * failures the container wrote its own page about it.
	 */
	private Reply written(Request request, Answer answer, MediaType writes) {
		Reply reply = Reply.of(answer.getStatus());
		answer.getHeaders().forEach((name, values) -> values.forEach(value -> reply.header(name, value)));
		if (answer.getEntity() == null) {
			if (answer.getStatus() >= 400) {
				return ContainerAnswers.errorPage(request, answer.getStatus(), Status.reasonFor(answer.getStatus()));
			}
			return reply;
		}
		if (!reply.hasHeader("Content-Type") && writes != null) {
			reply.contentType(writes.toString());
		}
		return reply.entity(Entities.write(mapper, answer.getEntity()));
	}

	/** What the request says its entity is. */
	private static final class Declared {

		private final MediaType type;

		Declared(MediaType type) {
			this.type = type;
		}

	}

}
