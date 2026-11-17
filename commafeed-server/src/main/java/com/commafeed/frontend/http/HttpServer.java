package com.commafeed.frontend.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.commafeed.frontend.servlet.Servlets;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webserver.DirectHandler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;

/**
 * The web server, and the order every request goes through on each of its two connectors.
 *
 * The replaced stack was a servlet container with a chain in front of it, and the order of that chain is visible in every answer, so it is
 * the order here:
 *
 * <ol>
 * <li>the target is read as it arrived and refused outright when the container would have refused its spelling;</li>
 * <li>a method the container did not dispatch is answered with the status alone;</li>
 * <li>an entity that arrived compressed is inflated;</li>
 * <li>the targets the filters were registered on are marked as not to be cached;</li>
 * <li>the request reaches the API, one of the servlets registered beside it, or the web client's own files;</li>
 * <li>a session the request was given has its cookie written onto the answer;</li>
 * <li>the answer is marked as depending on what the client accepts, and compressed if it does;</li>
 * <li>and a request that asked only for the headers of an answer is sent it without its entity.</li>
 * </ol>
 */
public final class HttpServer {

	private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

	/** What the container answered when asked what a target outside the API allows. */
	private static final String STATIC_ALLOW = "GET, HEAD, TRACE, OPTIONS";

	private static final Set<String> READS = Set.of("GET", "HEAD");

	private final ObjectMapper mapper;
	private final RestDispatcher rest;
	private final Servlets servlets;
	private final SessionStore sessions;
	private final AdminSurfaces admin;
	private final ExecutorService workers;
	private final int applicationPort;
	private final int adminPort;
	private final Routing webSockets;
	private WebServer server;

	public HttpServer(ObjectMapper mapper, RestDispatcher rest, Servlets servlets, SessionStore sessions, AdminSurfaces admin,
			ExecutorService workers, int applicationPort, int adminPort, Routing webSockets) {
		this.mapper = mapper;
		this.rest = rest;
		this.servlets = servlets;
		this.sessions = sessions;
		this.admin = admin;
		this.workers = workers;
		this.applicationPort = applicationPort;
		this.adminPort = adminPort;
		this.webSockets = webSockets;
	}

	/** Opens both connectors and answers once they are open, as the replaced launcher did. */
	public void start() {
		WebServer.Builder builder = WebServer.builder()
				.port(applicationPort)
				// a request the server itself refuses - one whose content type is not a media type,
				// say - never reaches the chain, and the container wrote its own page about it
				.directHandler(this::refused, DirectHandler.EventType.BAD_REQUEST);
		if (webSockets != null) {
			// the live-updates endpoint, which the configuration can switch off
			builder.addRouting(webSockets);
		}
		server = builder.addRouting(Routing.builder().any((request, response) -> receive(request, response, false)).build())
				.socket("admin", (socket, router) -> {
					socket.port(adminPort);
					router.addRouting(Routing.builder().any((request, response) -> receive(request, response, true)).build());
				})
				.build();
		server.start().await();
	}

	public void stop() {
		if (server != null) {
			server.shutdown().await();
		}
	}

	public int port() {
		return server.port();
	}

	/**
	 * Reads the whole entity, then answers on a worker thread.
	 *
	 * The entity is read in full first because a resource method talks to the database, which it cannot do on the thread the server reads
	 * the connection on.
	 */
	private void receive(ServerRequest request, ServerResponse response, boolean administrative) {
		request.content().as(byte[].class).thenAccept(entity -> workers.execute(() -> {
			try {
				write(response, answer(request, entity, administrative));
			} catch (RuntimeException failed) {
				log.error("the request could not be answered", failed);
				write(response, Reply.of(500));
			}
		})).exceptionallyAccept(failure -> write(response, Reply.of(400)));
	}

	/** The answer for one request, through the whole chain. */
	private Reply answer(ServerRequest incoming, byte[] entity, boolean administrative) {
		String rawPath = incoming.uri().getRawPath();
		RequestTarget target = RequestTarget.of(rawPath, incoming.query());
		Request request = new Request(incoming.method().name(), target, headers(incoming), entity);
		if (Targets.refused(rawPath)) {
			return ContainerAnswers.badTarget();
		}
		if (!RestDispatcher.dispatches(request.getMethod())) {
			return ContainerAnswers.bare(405);
		}
		String canonical = Targets.canonical(rawPath);
		Reply reply;
		try {
			inflate(request);
			reply = route(request, canonical, administrative);
		} catch (IllegalArgumentException notCompressed) {
			reply = logged(notCompressed);
		}
		return finish(request, marked(canonical, administrative, reply));
	}

	/**
	 * The container's own page about a request the server refused before the chain saw it.
	 *
	 * The chain had not run, so what the answer carries is the page alone - and the targets the filters were registered on are still marked
	 * as not to be cached, because the container marked its own pages that way wherever it wrote them.
	 */
	private DirectHandler.TransportResponse refused(DirectHandler.TransportRequest incoming, DirectHandler.EventType type,
			Http.ResponseStatus status, String message) {
		URI uri = URI.create(incoming.uri());
		Request request = new Request(incoming.method(), RequestTarget.of(uri.getRawPath(), uri.getRawQuery()), incoming.headers(),
				new byte[0]);
		Reply reply = marked(Targets.canonical(uri.getRawPath()), false, ContainerAnswers.errorPage(request, 400, "Bad Request"));
		DirectHandler.TransportResponse.Builder built = DirectHandler.TransportResponse.builder()
				.status(Http.ResponseStatus.create(reply.getStatus(), reply.getReason()));
		reply.getHeaders().forEach((name, values) -> values.forEach(value -> built.header(name, value)));
		return built.entity(reply.getEntity()).build();
	}

	/** The answer for a failure the server wrote down, which names what it wrote it down under. */
	private Reply logged(RuntimeException failure) {
		long id = ErrorAnswers.nextLogId();
		log.error(String.format("%016x", id), failure);
		Answer answer = ErrorAnswers.logged(id);
		return Reply.of(answer.getStatus()).contentType(MediaType.APPLICATION_JSON).entity(Entities.write(mapper, answer.getEntity()));
	}

	/** Routes the request to the API, to a servlet, to the client's files, or to the connector. */
	private Reply route(Request request, String canonical, boolean administrative) {
		if (administrative) {
			if ("OPTIONS".equals(request.getMethod())) {
				return Reply.of(200).setHeader("Allow", STATIC_ALLOW);
			}
			if (AdminSurfaces.tasks(canonical)) {
				// the tasks surface was registered on its own and answers for its own methods
				return admin.serve(request, canonical);
			}
			if (!READS.contains(request.getMethod())) {
				return outside(request, canonical);
			}
			Reply answered = admin.serve(request, canonical);
			return answered != null ? answered : ContainerAnswers.errorPage(request, 404, "Not Found");
		}
		if (canonical.equals(RestDispatcher.PREFIX) || canonical.startsWith(RestDispatcher.PREFIX + "/")) {
			return rest.dispatch(request);
		}
		if ("OPTIONS".equals(request.getMethod())) {
			return Reply.of(200).setHeader("Allow", STATIC_ALLOW);
		}
		if (servlets.claims(canonical) && READS.contains(request.getMethod())) {
			return servlets.serve(request, canonical);
		}
		if (READS.contains(request.getMethod())) {
			return Assets.serve(request, canonical);
		}
		return outside(request, canonical);
	}

	/**
	 * What the container answered for a method it dispatched but nothing outside the API answers.
	 *
	 * A post was answered with the container's own page naming the method; a put or a delete with the status alone; and a patch with the
	 * status that says the method is not implemented at all.
	 */
	private static Reply outside(Request request, String canonical) {
		switch (request.getMethod()) {
		case "POST":
			return ContainerAnswers.page(405, "HTTP method POST is not supported by this URL", canonical);
		case "PATCH":
			return ContainerAnswers.bare(501);
		default:
			return ContainerAnswers.bare(405);
		}
	}

	/** Marks the answer as not to be cached when the target is one the filters were put on. */
	private static Reply marked(String canonical, boolean administrative, Reply reply) {
		if (!administrative && CacheBusting.marks(canonical) && !reply.hasHeader("Cache-Control")) {
			reply.setHeader("Cache-Control", CacheBusting.NO_CACHE);
		}
		return reply;
	}

	/** The cookie of a session the request was given, then the compression, then the entity. */
	private Reply finish(Request request, Reply reply) {
		sessions.writeCookie(request, reply);
		Compression.apply(request, reply);
		if ("HEAD".equals(request.getMethod())) {
			reply.entity(new byte[0]);
		}
		return reply;
	}

	private static void inflate(Request request) {
		if (Inflation.inflates(request.getHeader("Content-Encoding"))) {
			request.setEntity(Inflation.inflate(request.getEntity()));
		}
	}

	private static Map<String, List<String>> headers(ServerRequest request) {
		Map<String, List<String>> headers = new LinkedHashMap<>();
		request.headers().toMap().forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
		return headers;
	}

	private static void write(ServerResponse response, Reply reply) {
		response.status(Http.ResponseStatus.create(reply.getStatus(), reply.getReason() == null ? "" : reply.getReason()));
		reply.getHeaders().forEach((name, values) -> values.forEach(value -> response.headers().add(name, value)));
		if (reply.getEntity().length == 0) {
			response.send();
		} else {
			response.send(Single.just(DataChunk.create(reply.getEntity())));
		}
	}

}
