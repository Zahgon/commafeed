package com.commafeed;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.JvmAttributeGaugeSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.commafeed.backend.Managed;
import com.commafeed.backend.dao.UnitOfWork;
import com.commafeed.backend.dao.UserDAO;
import com.commafeed.backend.feed.FeedRefreshEngine;
import com.commafeed.backend.service.UserService;
import com.commafeed.backend.service.db.DatabaseStartupService;
import com.commafeed.backend.service.db.H2MigrationService;
import com.commafeed.backend.task.ScheduledTask;
import com.commafeed.config.ConfigurationLoader;
import com.commafeed.config.DataSourceFactory;
import com.commafeed.config.Persistence;
import com.commafeed.config.ServerSettings;
import com.commafeed.frontend.auth.PasswordConstraintValidator;
import com.commafeed.frontend.auth.SecurityChecks;
import com.commafeed.frontend.http.AdminSurfaces;
import com.commafeed.frontend.http.Entities;
import com.commafeed.frontend.http.HealthChecks;
import com.commafeed.frontend.http.HttpServer;
import com.commafeed.frontend.http.RestDispatcher;
import com.commafeed.frontend.http.SessionStore;
import com.commafeed.frontend.resource.AdminREST;
import com.commafeed.frontend.resource.CategoryREST;
import com.commafeed.frontend.resource.EntryREST;
import com.commafeed.frontend.resource.FeedREST;
import com.commafeed.frontend.resource.ServerREST;
import com.commafeed.frontend.resource.UserREST;
import com.commafeed.frontend.resource.fever.FeverREST;
import com.commafeed.frontend.servlet.CustomCssServlet;
import com.commafeed.frontend.servlet.CustomJsServlet;
import com.commafeed.frontend.servlet.LogoutServlet;
import com.commafeed.frontend.servlet.NextUnreadServlet;
import com.commafeed.frontend.servlet.RobotsTxtDisallowAllServlet;
import com.commafeed.frontend.servlet.Servlets;
import com.commafeed.frontend.ws.WebSockets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Starts the server: reads the configuration, opens the database, wires the serving layer onto Helidon's web server and starts everything
 * the application runs in the background.
 *
 * The replaced framework did all of this from a list of bundles and a lifecycle of its own. What it did in which order is kept, because a
 * request that arrives before the schema is installed, or before the first account exists, would be answered differently: everything the
 * application runs is started first, and only then are the connectors opened, which is also why the launcher returns only once the server
 * answers.
 */
public class CommaFeedApplication {

	public static final String USERNAME_ADMIN = "admin";
	public static final String USERNAME_DEMO = "demo";

	public static final Instant STARTUP_TIME = Instant.now();

	private static final Logger log = LoggerFactory.getLogger(CommaFeedApplication.class);

	private final List<Managed> managed = new ArrayList<>();
	private HttpServer server;
	private ExecutorService workers;
	private SessionFactory sessionFactory;

	public static void main(String[] args) throws Exception {
		if (args.length < 2 || !"server".equals(args[0])) {
			System.err.println("usage: server <configuration file>");
			System.exit(1);
		}
		new CommaFeedApplication().run(Path.of(args[1]));
	}

	public void run(Path configurationFile) throws Exception {
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();

		CommaFeedConfiguration config = ConfigurationLoader.load(configurationFile);
		config.getLoggingSettings().apply();
		PasswordConstraintValidator.setStrict(config.getApplicationSettings().getStrictPasswordPolicy());

		DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
		migrateFileBasedH2(dataSourceFactory);

		MetricRegistry metrics = newMetricRegistry();
		DataSource dataSource = dataSourceFactory.build("hibernate");
		sessionFactory = Persistence.buildSessionFactory(dataSourceFactory, dataSource);

		SessionStore sessions = new SessionStore(config.getSessionHandlerFactory());
		Injector injector = Guice.createInjector(new CommaFeedModule(sessionFactory, config, metrics, sessions));
		ObjectMapper mapper = Entities.newObjectMapper();
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

		RestDispatcher rest = new RestDispatcher(mapper, validator, injector.getInstance(UnitOfWork.class),
				new SecurityChecks(injector.getInstance(UserDAO.class), injector.getInstance(UserService.class), config, sessions),
				sessions, metrics);
		rest.register(injector.getInstance(AdminREST.class));
		rest.register(injector.getInstance(CategoryREST.class));
		rest.register(injector.getInstance(EntryREST.class));
		rest.register(injector.getInstance(FeedREST.class));
		rest.register(injector.getInstance(ServerREST.class));
		rest.register(injector.getInstance(UserREST.class));
		rest.register(injector.getInstance(FeverREST.class));

		Servlets servlets = new Servlets();
		servlets.register("/next", injector.getInstance(NextUnreadServlet.class));
		servlets.register("/logout", injector.getInstance(LogoutServlet.class));
		servlets.register("/custom_css.css", injector.getInstance(CustomCssServlet.class));
		servlets.register("/custom_js.js", injector.getInstance(CustomJsServlet.class));
		if (Boolean.TRUE.equals(config.getApplicationSettings().getHideFromWebCrawlers())) {
			servlets.register("/robots.txt", injector.getInstance(RobotsTxtDisallowAllServlet.class));
		}

		HealthChecks health = new HealthChecks();
		health.register("deadlocks", () -> java.lang.management.ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null);
		health.register("hibernate", () -> {
			try (var connection = dataSource.getConnection();
					var statement = connection.prepareStatement(dataSourceFactory.getValidationQuery())) {
				statement.execute();
				return true;
			} catch (java.sql.SQLException notThere) {
				return false;
			}
		});

		startBackground(injector);

		workers = Executors.newCachedThreadPool();
		ServerSettings.ConnectorSettings application = config.getServerSettings().getApplicationConnector();
		ServerSettings.ConnectorSettings administration = config.getServerSettings().getAdminConnector();
		server = new HttpServer(mapper, rest, servlets, sessions, new AdminSurfaces(metrics, health, mapper), workers,
				application == null ? 0 : application.getPort(), administration == null ? 0 : administration.getPort(),
				WebSockets.routing(config, sessions, injector));
		server.start();
		log.info("CommaFeed is answering on port {}", server.port());

		Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown"));
	}

	/**
	 * Brings a database kept in a file up to the version the embedded engine can open.
	 *
	 * The replaced framework ran this as the first of its bundles, before anything had opened the database, because the engine cannot open
	 * a file an older version of it wrote.
	 */
	private static void migrateFileBasedH2(DataSourceFactory dataSourceFactory) {
		String url = dataSourceFactory.getUrl();
		if (!url.startsWith("jdbc:h2:") || url.startsWith("jdbc:h2:mem:")) {
			return;
		}
		String name = url.substring("jdbc:h2:".length()).split(";")[0];
		new H2MigrationService().migrateIfNeeded(Path.of(name + ".mv.db"), dataSourceFactory.getUser(), dataSourceFactory.getPassword());
	}

	/** Everything the application runs on its own, started before the connectors are opened. */
	private void startBackground(Injector injector) throws Exception {
		Set<ScheduledTask> tasks = injector.getInstance(Key.get(new TypeLiteral<>() {
		}));
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Math.max(1, tasks.size()), runnable -> {
			Thread thread = new Thread(runnable, "task-scheduler");
			thread.setDaemon(true);
			return thread;
		});
		for (ScheduledTask task : tasks) {
			task.register(scheduler);
		}
		managed.add(injector.getInstance(DatabaseStartupService.class));
		managed.add(injector.getInstance(FeedRefreshEngine.class));
		for (Managed each : managed) {
			each.start();
		}
	}

	/** The measurements about the process itself, which the second connector answers with. */
	private static MetricRegistry newMetricRegistry() {
		MetricRegistry metrics = new MetricRegistry();
		metrics.register("jvm.attribute", new JvmAttributeGaugeSet());
		metrics.register("jvm.classloader", new ClassLoadingGaugeSet());
		metrics.register("jvm.gc", new GarbageCollectorMetricSet());
		metrics.register("jvm.memory", new MemoryUsageGaugeSet());
		metrics.register("jvm.threads", new ThreadStatesGaugeSet());
		return metrics;
	}

	private void stop() {
		for (int at = managed.size() - 1; at >= 0; at--) {
			try {
				managed.get(at).stop();
			} catch (Exception failed) {
				log.warn("could not stop {}", managed.get(at).getClass().getSimpleName(), failed);
			}
		}
		if (server != null) {
			server.stop();
		}
		if (workers != null) {
			workers.shutdownNow();
		}
		if (sessionFactory != null) {
			sessionFactory.close();
		}
	}

}
