package com.commafeed.config;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;

import com.commafeed.backend.model.AbstractModel;
import com.commafeed.backend.model.Feed;
import com.commafeed.backend.model.FeedCategory;
import com.commafeed.backend.model.FeedEntry;
import com.commafeed.backend.model.FeedEntryContent;
import com.commafeed.backend.model.FeedEntryStatus;
import com.commafeed.backend.model.FeedEntryTag;
import com.commafeed.backend.model.FeedSubscription;
import com.commafeed.backend.model.User;
import com.commafeed.backend.model.UserRole;
import com.commafeed.backend.model.UserSettings;

/**
 * Builds the Hibernate session factory the data access classes run against.
 *
 * The replaced framework's bundle built it, and three of the settings it put on it are part of how the server behaves rather than of how it
 * is written, so they are set here too: the session context is the managed one, which is what lets a unit of work bind a session for the
 * length of a request; identifiers come from the pooled-lo optimiser, so a row created by one request gets the identifier the next request
 * reads it back under; and the statements of a unit of work are ordered and batched.
 */
public final class Persistence {

	/** The entities the server maps, in the order the replaced bundle was given them. */
	private static final Class<?>[] ENTITIES = { AbstractModel.class, Feed.class, FeedCategory.class, FeedEntry.class,
			FeedEntryContent.class, FeedEntryStatus.class, FeedEntryTag.class, FeedSubscription.class, User.class, UserRole.class,
			UserSettings.class };

	private Persistence() {
	}

	public static SessionFactory buildSessionFactory(DataSourceFactory factory, DataSource dataSource) {
		Configuration configuration = new Configuration();
		configuration.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "managed");
		configuration.setProperty(AvailableSettings.USE_GET_GENERATED_KEYS, "true");
		configuration.setProperty(AvailableSettings.GENERATE_STATISTICS, "true");
		configuration.setProperty(AvailableSettings.PREFERRED_POOLED_OPTIMIZER, "pooled-lo");
		configuration.setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, "50");
		configuration.setProperty(AvailableSettings.BATCH_VERSIONED_DATA, "true");
		configuration.setProperty(AvailableSettings.ORDER_INSERTS, "true");
		configuration.setProperty(AvailableSettings.ORDER_UPDATES, "true");
		factory.getProperties().forEach(configuration::setProperty);
		configuration.getProperties().put(AvailableSettings.DATASOURCE, dataSource);
		for (Class<?> entity : ENTITIES) {
			configuration.addAnnotatedClass(entity);
		}
		return configuration.buildSessionFactory();
	}

}
