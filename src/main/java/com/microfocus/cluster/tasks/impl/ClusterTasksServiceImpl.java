/*
	(c) Copyright 2018 Micro Focus or one of its affiliates.
	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
	You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and limitations under the License.
 */

package com.microfocus.cluster.tasks.impl;

import com.microfocus.cluster.tasks.api.ClusterTasksProcessorScheduled;
import com.microfocus.cluster.tasks.api.ClusterTasksService;
import com.microfocus.cluster.tasks.api.ClusterTasksServiceConfigurerSPI;
import com.microfocus.cluster.tasks.api.builders.TaskBuilders;
import com.microfocus.cluster.tasks.api.dto.ClusterTask;
import com.microfocus.cluster.tasks.api.dto.ClusterTaskPersistenceResult;
import com.microfocus.cluster.tasks.api.enums.CTPPersistStatus;
import com.microfocus.cluster.tasks.api.enums.ClusterTaskStatus;
import com.microfocus.cluster.tasks.api.enums.ClusterTaskType;
import com.microfocus.cluster.tasks.api.enums.ClusterTasksDataProviderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

/**
 * Created by gullery on 08/05/2016.
 * <p>
 * Default implementation of ClusterTasksService
 */

public class ClusterTasksServiceImpl implements ClusterTasksService {
	private final Logger logger = LoggerFactory.getLogger(ClusterTasksServiceImpl.class);

	private final String RUNTIME_INSTANCE_ID = UUID.randomUUID().toString();
	private final CompletableFuture<Boolean> readyPromise = new CompletableFuture<>();
	private final Map<ClusterTasksDataProviderType, ClusterTasksDataProvider> dataProvidersMap = new LinkedHashMap<>();
	private final Map<String, ClusterTasksProcessorBase> processorsMap = new LinkedHashMap<>();
	private final ExecutorService dispatcherExecutor = Executors.newSingleThreadExecutor(new ClusterTasksDispatcherThreadFactory());
	private final ExecutorService maintainerExecutor = Executors.newSingleThreadExecutor(new ClusterTasksMaintainerThreadFactory());
	private final SystemWorkersConfigurer workersConfigurer = new SystemWorkersConfigurer();
	private final ClusterTasksDispatcher dispatcher = new ClusterTasksDispatcher(workersConfigurer);
	private final ClusterTasksMaintener maintainer = new ClusterTasksMaintener(workersConfigurer);
	private ClusterTasksServiceConfigurerSPI serviceConfigurer;
	private ClusterTasksServiceSchemaManager schemaManager;

	private static final Long MAX_TIME_TO_RUN_DEFAULT = 1000 * 60L;

	@Autowired
	private ClusterTasksServiceImpl(ClusterTasksServiceConfigurerSPI serviceConfigurer, ClusterTasksServiceSchemaManager schemaManager) {
		this.serviceConfigurer = serviceConfigurer;
		this.schemaManager = schemaManager;
		logger.info("------------------------------------------------");
		logger.info("------------- Cluster Tasks Service ------------");

		if (serviceConfigurer.getConfigReadyLatch() == null) {
			initService();
		} else {
			logger.info("starting listener on configuration readiness...");
			serviceConfigurer.getConfigReadyLatch().handleAsync((value, error) -> {
				logger.info("listener on configuration readiness resolved; value: " + value + (error != null ? (", error: " + error) : ""));
				if (value == null || !value) {
					readyPromise.complete(false);
					if (error != null) {
						logger.error("hosting application FAILED to provide configuration, this instance of CTS is not workable", error);
					} else {
						logger.error("hosting application FAILED to provide configuration, this instance of CTS is not workable");
					}
				} else {
					try {
						initService();
					} catch (Throwable throwable) {
						logger.error("FAILED to initialize ClusterTasksService, this instance of CTS is not workable", throwable);
					}
				}

				return null;
			});
		}
	}

	@Autowired(required = false)
	private void registerProcessors(List<ClusterTasksProcessorBase> processors) {
		if (processors.size() > 500) {
			throw new IllegalStateException("processors number is higher than allowed (500)");
		}

		processors.forEach(processor -> {
			String type = processor.getType();
			String className = processor.getClass().getName();
			if (type == null || type.isEmpty()) {
				logger.error("processor " + className + " rejected: type MUST NOT be null nor empty");
			} else if (type.length() > 40) {
				logger.error("processor " + className + " rejected: type MUST NOT exceed 40 characters, found " + type.length() + " (" + type + ")");
			} else if (processorsMap.containsKey(type)) {
				logger.error("processor " + className + " rejected: more than one implementation pretend to process '" + type + "' tasks");
			} else {
				processorsMap.put(type, processor);
			}
		});
		if (!processorsMap.isEmpty()) {
			logger.info(processorsMap.size() + " CTPs are registered in this instance:");
			processorsMap.keySet().forEach(key -> logger.info("\t\t" + key));
		} else {
			logger.info("none CTPs are registered in this instance");
		}
	}

	@Override
	public String getInstanceID() {
		return RUNTIME_INSTANCE_ID;
	}

	@Override
	public CompletableFuture<Boolean> getReadyPromise() {
		return readyPromise;
	}

	@Override
	public ClusterTaskPersistenceResult[] enqueueTasks(ClusterTasksDataProviderType dataProviderType, String processorType, ClusterTask... tasks) {
		if (!readyPromise.isDone()) {
			throw new IllegalStateException("cluster tasks service has not yet been initialized; either postpone tasks submission or listen to completion of [clusterTasksService].getReadyPromise()");
		}
		if (readyPromise.isCompletedExceptionally()) {
			throw new IllegalStateException("cluster tasks service failed to initialize; check previous logs for a root cause");
		}

		if (dataProviderType == null) {
			throw new IllegalArgumentException("data provider type MUST NOT be null");
		}
		if (processorType == null || processorType.isEmpty()) {
			throw new IllegalArgumentException("processor type MUST NOT be null nor empty");
		}
		if (processorType.length() > 40) {
			throw new IllegalArgumentException("processor type MAY NOT exceed 40 characters; given " + processorType.length() + " (" + processorType + ")");
		}
		if (tasks == null || tasks.length == 0) {
			throw new IllegalArgumentException("tasks array MUST NOT be null nor empty");
		}

		ClusterTasksDataProvider dataProvider = dataProvidersMap.get(dataProviderType);
		if (dataProvider != null) {
			TaskInternal[] taskInternals = convertTasks(tasks, processorType);
			return dataProvidersMap.get(dataProviderType).storeTasks(taskInternals);
		} else {
			throw new IllegalArgumentException("unknown data provider of type '" + processorType + "'");
		}
	}

	@Deprecated
	@Override
	public int countTasks(ClusterTasksDataProviderType dataProviderType, String processorType, ClusterTaskStatus... statuses) {
		Set<ClusterTaskStatus> statusSet = Arrays.stream(statuses).collect(Collectors.toSet());
		return dataProvidersMap.get(dataProviderType).countTasks(processorType, statusSet);
	}

	private void initService() {
		logger.info("starting initialization");
		if (serviceConfigurer.getAdministrativeDataSource() != null) {
			logger.info("performing schema maintenance");
			schemaManager.executeSchemaMaintenance(serviceConfigurer.getDbType(), serviceConfigurer.getAdministrativeDataSource());
		} else {
			logger.info("administrative DataSource not provided, skipping schema maintenance");
		}

		setupDataProviders();

		dispatcherExecutor.execute(dispatcher);
		maintainerExecutor.execute(maintainer);
		logger.info("tasks dispatcher and maintenance threads initialized");

		logger.info("CTS is configured & initialized, instance ID: " + RUNTIME_INSTANCE_ID);
		readyPromise.complete(true);

		ensureScheduledTasksInitialized();
		logger.info("scheduled tasks initialization verified");
	}

	private void setupDataProviders() {
		//  DB
		if (serviceConfigurer.getDbType() != null) {
			if (serviceConfigurer.getDataSource() == null) {
				throw new IllegalStateException("DataSource is not provided, while DBType declared to be '" + serviceConfigurer.getDbType() + "'");
			}
			switch (serviceConfigurer.getDbType()) {
				case MSSQL:
					dataProvidersMap.put(ClusterTasksDataProviderType.DB, new MsSqlDbDataProvider(this, serviceConfigurer));
					break;
				case ORACLE:
					dataProvidersMap.put(ClusterTasksDataProviderType.DB, new OracleDbDataProvider(this, serviceConfigurer));
					break;
				case POSTGRESQL:
					dataProvidersMap.put(ClusterTasksDataProviderType.DB, new PostgreSqlDbDataProvider(this, serviceConfigurer));
					break;
				default:
					logger.error("DB type '" + serviceConfigurer.getDbType() + "' has no data provider, DB oriented tasking won't be available");
					break;
			}
		}

		//  summary
		if (!dataProvidersMap.isEmpty()) {
			logger.info("summarizing registered data providers:");
			dataProvidersMap.forEach((type, provider) -> logger.info("\t\t" + type + ": " + provider.getClass().getSimpleName()));
		} else {
			throw new IllegalStateException("no (relevant) data providers available");
		}
	}

	private void ensureScheduledTasksInitialized() {
		processorsMap.forEach((type, processor) -> {
			if (processor instanceof ClusterTasksProcessorScheduled) {
				logger.info("performing initial scheduled task upsert for the first-ever-run case on behalf of " + type);
				ClusterTasksDataProvider dataProvider = dataProvidersMap.get(processor.getDataProviderType());
				ClusterTaskPersistenceResult enqueueResult;
				int maxEnqueueAttempts = 20, enqueueAttemptsCount = 0;
				ClusterTask clusterTask = TaskBuilders.uniqueTask()
						.setUniquenessKey(type)
						.setMaxTimeToRunMillis(((ClusterTasksProcessorScheduled) processor).getMaxTimeToRun())
						.build();
				TaskInternal[] scheduledTasks = convertTasks(new ClusterTask[]{clusterTask}, type);
				scheduledTasks[0].taskType = ClusterTaskType.SCHEDULED;
				do {
					enqueueAttemptsCount++;
					enqueueResult = dataProvider.storeTasks(scheduledTasks[0])[0];
					if (enqueueResult.getStatus() == CTPPersistStatus.SUCCESS) {
						logger.info("initial task for " + type + " created");
						break;
					} else if (enqueueResult.getStatus() == CTPPersistStatus.UNIQUE_CONSTRAINT_FAILURE) {
						logger.info("failed to create initial scheduled task for " + type + " with unique constraint violation, assuming that task was already created, will not reattempt");
						break;
					} else {
						logger.error("failed to create scheduled task for " + type + " with error " + enqueueResult.getStatus() + "; will reattempt for more " + (maxEnqueueAttempts - enqueueAttemptsCount) + " times");
						try {
							Thread.sleep(3000);
						} catch (InterruptedException ie) {
							logger.warn("interrupted while breathing, proceeding with reattempts");
						}
					}
				} while (enqueueAttemptsCount < maxEnqueueAttempts);
			}
		});
	}

	private TaskInternal[] convertTasks(ClusterTask[] sourceTasks, String targetProcessorType) {
		TaskInternal[] result = new TaskInternal[sourceTasks.length];
		for (int i = 0; i < sourceTasks.length; i++) {
			ClusterTask source = sourceTasks[i];
			if (source == null) {
				throw new IllegalArgumentException("of the submitted tasks NONE SHOULD BE NULL");
			}

			TaskInternal target = new TaskInternal();

			if (source.getUniquenessKey() != null) {
				target.uniquenessKey = source.getUniquenessKey();
				target.concurrencyKey = source.getUniquenessKey();
				if (source.getConcurrencyKey() != null) {
					logger.warn("concurrency key MUST NOT be used along with uniqueness key, falling back to uniqueness key as concurrency key");
				}
			} else {
				target.uniquenessKey = UUID.randomUUID().toString();
				target.concurrencyKey = source.getConcurrencyKey();
			}

			target.processorType = targetProcessorType;
			target.orderingFactor = null;
			target.delayByMillis = source.getDelayByMillis() == null ? (Long) 0L : source.getDelayByMillis();
			target.maxTimeToRunMillis = source.getMaxTimeToRunMillis() == null || source.getMaxTimeToRunMillis() == 0 ? MAX_TIME_TO_RUN_DEFAULT : source.getMaxTimeToRunMillis();
			target.body = source.getBody() == null || source.getBody().isEmpty() ? null : source.getBody();
			target.taskType = ClusterTaskType.REGULAR;

			result[i] = target;
		}

		return result;
	}

	private static final class ClusterTasksDispatcherThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread result = new Thread(runnable);
			result.setName("CTS Dispatcher; TID: " + result.getId());
			result.setDaemon(true);
			return result;
		}
	}

	private static final class ClusterTasksMaintainerThreadFactory implements ThreadFactory {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread result = new Thread(runnable);
			result.setName("CTS Maintainer; TID: " + result.getId());
			result.setDaemon(true);
			return result;
		}
	}

	/**
	 * Configurer with a very limited creation access level but wider read access level for protected internal configuration flows
	 * - for a most reasons this class is just a proxy for getting ClusterTasksService private properties in a safe way
	 */
	final class SystemWorkersConfigurer {
		private SystemWorkersConfigurer() {

		}

		String getInstanceID() {
			return RUNTIME_INSTANCE_ID;
		}

		boolean isCTSServiceEnabled() {
			long DURATION_THRESHOLD = 5;
			long foreignCallStart = System.currentTimeMillis();
			boolean isEnabled = serviceConfigurer.isEnabled();
			long foreignCallDuration = System.currentTimeMillis() - foreignCallStart;
			if (foreignCallDuration > DURATION_THRESHOLD) {
				logger.warn("call to a foreign method 'isEnabled' took more than " + DURATION_THRESHOLD + "ms (" + foreignCallDuration + "ms)");
			}
			return isEnabled;
		}

		Map<ClusterTasksDataProviderType, ClusterTasksDataProvider> getDataProvidersMap() {
			return dataProvidersMap;
		}

		Map<String, ClusterTasksProcessorBase> getProcessorsMap() {
			return processorsMap;
		}

		ClusterTasksServiceConfigurerSPI getCTSServiceConfigurer() {
			return serviceConfigurer;
		}
	}
}
