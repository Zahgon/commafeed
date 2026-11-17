package com.commafeed.frontend.http;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.Locale;

import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * The second connector: the surfaces that answer about the process rather than about the feeds.
 *
 * The replaced framework opened a connector of its own and registered five surfaces on it, and what each of them answers - down to the
 * character set on it and whether it says the answer is not to be cached - is what a client reads, so all of it is kept:
 *
 * <ul>
 * <li>a menu of the other surfaces, as a document to look at;</li>
 * <li>a word to say the process is answering at all;</li>
 * <li>what every check of the process says, as a JSON document;</li>
 * <li>everything the process is measuring, as a JSON document;</li>
 * <li>what every thread of the process is doing, as plain text;</li>
 * <li>and the tasks the process can be asked to run, which alone among them does not say that its answer is not to be cached.</li>
 * </ul>
 */
public final class AdminSurfaces {

	private static final String MENU = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\"\n"
			+ "        \"http://www.w3.org/TR/html4/loose.dtd\">\n" + "<html>\n<head>\n  <title>Metrics</title>\n</head>\n<body>\n"
			+ "  <h1>Operational Menu</h1>\n  <ul>\n" + "    <li><a href=\"/metrics?pretty=true\">Metrics</a></li>\n"
			+ "    <li><a href=\"/ping\">Ping</a></li>\n" + "    <li><a href=\"/threads\">Threads</a></li>\n"
			+ "    <li><a href=\"/healthcheck?pretty=true\">Healthcheck</a></li>\n" + "    <li><a href=\"/pprof\">CPU Profile</a></li>\n"
			+ "    <li><a href=\"/pprof?state=blocked\">CPU Contention</a></li>\n" + "  </ul>\n</body>\n</html>\n";

	/** The tasks the process can be asked to run, in the order the surface lists them. */
	private static final String TASKS = "gc\nlog-level\n";

	private final MetricRegistry metrics;
	private final HealthChecks health;
	private final ObjectMapper mapper;

	public AdminSurfaces(MetricRegistry metrics, HealthChecks health, ObjectMapper mapper) {
		this.metrics = metrics;
		this.health = health;
		this.mapper = mapper;
	}

	/** Whether the target is the tasks surface, which was registered on its own. */
	public static boolean tasks(String path) {
		return "/tasks".equals(path) || path.startsWith("/tasks/");
	}

	/** What this connector answers, or null when it answers nothing on this target. */
	public Reply serve(Request request, String path) {
		boolean head = "HEAD".equals(request.getMethod());
		if (tasks(path)) {
			return task(request, path);
		}
		switch (path) {
		case "/":
			return marked(Reply.of(200).contentType("text/html;charset=utf-8"), head, MENU);
		case "/ping":
			return marked(Reply.of(200).contentType(head ? "text/plain" : "text/plain;charset=iso-8859-1"), head, "pong\n");
		case "/threads":
			return marked(Reply.of(200).contentType("text/plain"), head, threadDump());
		case "/healthcheck":
			return marked(Reply.of(200).contentType(MediaType.APPLICATION_JSON), head,
					new String(Entities.write(mapper, health.run()), java.nio.charset.StandardCharsets.UTF_8));
		case "/metrics":
		case "/metrics/":
			return marked(Reply.of(200).contentType(MediaType.APPLICATION_JSON), head,
					new String(Entities.write(mapper, metrics), java.nio.charset.StandardCharsets.UTF_8));
		case "/pprof":
			return profile(request, head);
		default:
			return null;
		}
	}

	/**
	 * The tasks surface, which the replaced framework registered as a surface of its own.
	 *
	 * It is the one surface here that does not say its answer is not to be cached, and the one that answers a target it does not know, or a
	 * method it does not answer, with the status alone.
	 */
	private Reply task(Request request, String path) {
		String name = path.equals("/tasks") || path.equals("/tasks/") ? "" : path.substring("/tasks/".length());
		if (name.isEmpty()) {
			if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
				return ContainerAnswers.bare(405);
			}
			Reply reply = Reply.of(200).contentType("HEAD".equals(request.getMethod()) ? "text/plain" : "text/plain;charset=iso-8859-1");
			return "HEAD".equals(request.getMethod()) ? reply : reply.entity(TASKS);
		}
		if (!"gc".equals(name) && !"log-level".equals(name)) {
			return ContainerAnswers.bare(404);
		}
		if (!"POST".equals(request.getMethod())) {
			return ContainerAnswers.bare(405);
		}
		Reply reply = Reply.of(200).contentType("text/plain;charset=utf-8");
		if ("gc".equals(name)) {
			System.gc();
			return reply.entity("Running GC...\nDone!\n");
		}
		return logLevel(request, reply);
	}

	/**
	 * Sets the level of each logger the request names, and says so, as the replaced task did.
	 *
	 * The request may name a logger more than once and each one is answered on a line of its own; a request that names none sets nothing
	 * and the answer carries nothing, which is what a request with an entity the task does not read is. A level the request does not carry
	 * puts a logger back to the one it inherits, and the line then says so.
	 */
	private static Reply logLevel(Request request, Reply reply) {
		List<String> loggers = request.getTarget().getQueryParameters().getOrDefault("logger", List.of());
		if (loggers.isEmpty()) {
			return reply;
		}
		String asked = request.getTarget().getQueryParameter("level");
		Level level = asked == null || asked.isEmpty() ? null : Level.toLevel(asked, null);
		StringBuilder said = new StringBuilder();
		for (String logger : loggers) {
			((Logger) LoggerFactory.getLogger(logger)).setLevel(level);
			said.append("Configured logging level for ").append(logger).append(" to ").append(level).append('\n');
		}
		return reply.entity(said.toString());
	}

	/**
	 * The sampled profile, for as long and as often as the request asks for.
	 *
	 * The replaced servlet sampled the threads that are running, or those that are blocked when the request asks for the contended profile,
	 * which is the second of the two the menu points at.
	 */
	private static Reply profile(Request request, boolean head) {
		Thread.State wanted = "blocked".equalsIgnoreCase(request.getTarget().getQueryParameter("state")) ? Thread.State.BLOCKED
				: Thread.State.RUNNABLE;
		Reply reply = Reply.of(200).contentType("pprof/raw").setHeader("Cache-Control", CacheBusting.NO_CACHE);
		if (head) {
			return reply;
		}
		return reply.entity(CpuProfile.sample(asked(request, "duration", CpuProfile.SECONDS),
				asked(request, "frequency", CpuProfile.FREQUENCY), wanted));
	}

	/** A number the request carries, or the one the replaced servlet used when it carried none. */
	private static int asked(Request request, String name, int whenAbsent) {
		String value = request.getTarget().getQueryParameter(name);
		if (value == null || value.isEmpty()) {
			return whenAbsent;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException notANumber) {
			return whenAbsent;
		}
	}

	/** Marks the answer as not to be cached, which every surface here but the tasks one did. */
	private static Reply marked(Reply reply, boolean head, String entity) {
		reply.setHeader("Cache-Control", CacheBusting.NO_CACHE);
		return head ? reply : reply.entity(entity);
	}

	private static String threadDump() {
		StringBuilder dump = new StringBuilder();
		for (ThreadInfo thread : ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)) {
			dump.append('"')
					.append(thread.getThreadName())
					.append("\" id=")
					.append(thread.getThreadId())
					.append(" state=")
					.append(thread.getThreadState().name().toLowerCase(Locale.ROOT))
					.append('\n');
			for (StackTraceElement frame : thread.getStackTrace()) {
				dump.append("    at ").append(frame).append('\n');
			}
			dump.append('\n');
		}
		return dump.toString();
	}

}
