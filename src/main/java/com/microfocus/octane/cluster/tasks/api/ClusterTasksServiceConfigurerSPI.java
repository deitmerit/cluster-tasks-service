package com.microfocus.octane.cluster.tasks.api;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;

public interface ClusterTasksServiceConfigurerSPI {
	int MINIMAL_POLL_INTERVAL = 703;
	int DEFAULT_POLL_INTERVAL = 1023;
	int MINIMAL_GC_INTERVAL = 7131;
	int DEFAULT_GC_INTERVAL = 13039;

	enum DBType {ORACLE, MSSQL}

	/**
	 * OOTB provided promise that MUST BE resolved by the hosting application in order to allow ClusterTasksService to run correctly
	 * - resolved with TRUE when the configuration ready and ClusterTasksService may start it's routine
	 * - resolved with FALSE when hosting application decided that it fails to provide ClusterTasksService it's required configuration (DB connectivity, for instance)
	 *
	 * @return promise on configuration readiness
	 */
	CompletableFuture<Boolean> getConfigReadyLatch();

	/**
	 * return interval in millis to breathe between the tasks polling requests
	 * if result is lower than minimum figure - the MINIMAL_POLL_INTERVAL will be used
	 * if result is NULL - the DEFAULT_POLL_INTERVAL will be used
	 *
	 * @return interval in millis or NULL (default value will be taken)
	 */
	Integer getTasksPollIntervalMillis();

	/**
	 * return interval in millis to breathe between the GC cycles
	 * if result is lower than minimum figure - the MINIMAL_GC_INTERVAL will be used
	 * if result is NULL - the DEFAULT_GC_INTERVAL will be used
	 *
	 * @return either number of millis to wait between intervals or NULL (default value will be taken)
	 */
	Integer getGCIntervalMillis();

	/**
	 * returns DB type, that ClusterTasksService's tables resides in
	 *
	 * @return db type; MUST NOT be null
	 */
	DBType getDbType();

	/**
	 * returns data source to the DB, that the ClusterTasksService's tables reside in
	 *
	 * @return data source; MUST NOT be null
	 */
	DataSource getDataSource();

	/**
	 * this API's purpose is to early react on host environment that is not providing tasks ID (thus not being able to create tasks, working in processing mode only)
	 * PAY ATTENTION: having this flag as 'false' means that the hosting service will also NOT ATTEMPT TO ADD GC TASK (it will still handle it if the task is already there)
	 * [YG] this API is deprecated since it is serving the 'obtainAvailableTaskID' case only; both API should be removed in the future
	 *
	 * @return task creation supported flag
	 */
	@Deprecated
	default boolean tasksCreationSupported() {
		return false;
	}

	/**
	 * returns valid ID to be used to mark the newly created task with
	 * [YG] this API is deprecated; striving to manage the tasks' ID generation/provisioning internally
	 *
	 * @return task ID
	 */
	@Deprecated
	default long obtainAvailableTaskID() {
		throw new IllegalStateException("not implemented flow in this service's context");
	}
}