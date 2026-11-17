package com.commafeed.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolProperties;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * The {@code database} section of the configuration file, and the pool it builds.
 *
 * The replaced framework owned this section; its spelling is kept so that the same configuration file still reads, and the properties it
 * carries are still handed to Hibernate as well as to the driver, which is what the replaced framework did with them.
 */
@Getter
@Setter
public class DataSourceFactory {

	@NotNull
	@JsonProperty
	private String driverClass;

	@NotNull
	@JsonProperty
	private String url;

	@JsonProperty
	private String user;

	@JsonProperty
	private String password;

	@JsonProperty
	private Map<String, String> properties = new LinkedHashMap<>();

	@JsonProperty
	private String validationQuery = "/* Health Check */ SELECT 1";

	@Min(0)
	@JsonProperty
	private int minSize = 10;

	@Min(1)
	@JsonProperty
	private int maxSize = 100;

	@Min(0)
	@JsonProperty
	private int initialSize = 10;

	@JsonProperty
	private boolean autoCommitByDefault = true;

	@JsonProperty
	private Duration maxWaitForConnection = Duration.seconds(30);

	@JsonProperty
	private Duration validationQueryTimeout = Duration.seconds(5);

	/** A pool for the configured database, under the given name. */
	public DataSource build(String name) {
		PoolProperties pool = new PoolProperties();
		pool.setName(name);
		pool.setDriverClassName(driverClass);
		pool.setUrl(url);
		pool.setUsername(user);
		pool.setPassword(password);
		pool.setDefaultAutoCommit(autoCommitByDefault);
		pool.setInitialSize(initialSize);
		pool.setMinIdle(minSize);
		pool.setMaxIdle(maxSize);
		pool.setMaxActive(maxSize);
		pool.setMaxWait((int) maxWaitForConnection.toMilliseconds());
		pool.setTestOnBorrow(true);
		pool.setValidationQuery(validationQuery);
		pool.setValidationQueryTimeout((int) validationQueryTimeout.toSeconds());
		Properties driverProperties = new Properties();
		driverProperties.putAll(properties);
		pool.setDbProperties(driverProperties);
		return new org.apache.tomcat.jdbc.pool.DataSource(pool);
	}

}
