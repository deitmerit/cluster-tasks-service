/*
	(c) Copyright 2018 Micro Focus or one of its affiliates.
	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
	You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and limitations under the License.
 */

package com.microfocus.cluster.tasks.impl;

import com.microfocus.cluster.tasks.api.dto.ClusterTaskPersistenceResult;
import com.microfocus.cluster.tasks.api.enums.ClusterTaskStatus;
import com.microfocus.cluster.tasks.api.enums.ClusterTasksDataProviderType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by gullery on 08/05/2016.
 *
 * API definition of cluster tasks data provider; implementation MUST be thread/scope safe
 * This API is intended for internal implementation only and should NOT be used/implemented by no mean by hosting application
 */

interface ClusterTasksDataProvider {

	/**
	 * Provider type
	 *
	 * @return Tasks Provider type
	 */
	ClusterTasksDataProviderType getType();

	/**
	 * Verifies that the data provider is ready to be polled for tasks and/or maintenance
	 *
	 * @return readiness status
	 */
	boolean isReady();

	/**
	 * Stores task for future retrieval
	 *
	 * @param tasks one or more tasks content to be pushed into the queue
	 * @return an array of Optionals, corresponding to the array of the tasks, having either the task ID in case of successful push or an exception in case of failure
	 */
	ClusterTaskPersistenceResult[] storeTasks(TaskInternal... tasks);

	/**
	 * Attempts to retrieve next valid task per type, marks the retrieved task as running and possible checks is there are more tasks valid to be executed
	 *
	 * @param processors data set of all registered processors, that data provider should try to find tasks for
	 */
	void retrieveAndDispatchTasks(Map<String, ClusterTasksProcessorBase> processors);

	/**
	 * Retrieves task's body
	 *
	 * @param taskId         task's body will always have an ID of the task itself
	 * @param partitionIndex index of table the body was stored to
	 * @return task's body
	 */
	String retrieveTaskBody(Long taskId, Long partitionIndex);

	/**
	 * Updates task as FINISHED, thus releasing the processor to take next task and make this task valid for GC
	 *
	 * @param taskId the value that was assigned to a task in process of creation
	 */
	void updateTaskToFinished(Long taskId);

	/**
	 * Implementation should perform a clean up of an items in storage that may be considered as 'garbage'
	 * Items that found to be 'staled' but are not considered to be 'garbage' should be handled accordingly to each own specific logic
	 */
	void handleGarbageAndStaled();

	/**
	 * Implementation should perform a re-scheduling of a SCHEDULED tasks ONLY
	 * Implementation MAY verify whether the tasks are already scheduled or not yet in order to prevent attempt to insert duplicate task
	 *
	 * @param candidatesToReschedule list of tasks of type SCHEDULE that should be re-run
	 */
	void reinsertScheduledTasks(List<TaskInternal> candidatesToReschedule);

	/**
	 * Implementation should provide a counter for all tasks in the specified status existing in the storage grouped be PROCESSOR TYPE
	 *
	 * @param status only tasks of this status will be counted; MUST NOT be null
	 * @return count result mapped be PROCESSOR TYPE
	 */
	Map<String, Integer> countTasks(ClusterTaskStatus status);

	/**
	 * Implementation should provide a counter of all tasks existing in the Storage right to the moment of query
	 * Counter always works within boundaries of a specific processor's tasks type
	 * Counter should take into consideration OPTIONAL concurrency key parameter
	 * Counter should take into consideration OPTIONAL statuses list parameter
	 *
	 * @param processorType type of the processor, that it's tasks are looked up; MUST NOT be null nor empty
	 * @param statuses      statuses list to take into consideration, OPTIONAL; MUST NOT be null, MAY be an empty set
	 * @return number of tasks of the specified type [AND, optionally, concurrencyKey] found in DB
	 */
	@Deprecated
	int countTasks(String processorType, Set<ClusterTaskStatus> statuses);

	/**
	 * Implementation should provide a counter of all tasks existing in the Storage right to the moment of query
	 * Counter always works within boundaries of a specific processor's tasks type
	 * Counter should take into consideration OPTIONAL concurrency key parameter
	 * Counter should take into consideration OPTIONAL statuses list parameter
	 *
	 * @param processorType  type of the processor, that it's tasks are looked up; MUST NOT be null nor empty
	 * @param concurrencyKey concurrency narrower, OPTIONAL; when equals to null or empty will not be taken into consideration
	 * @param statuses       statuses list to take into consideration, OPTIONAL; MUST NOT be null, MAY be an empty set
	 * @return number of tasks of the specified type [AND, optionally, concurrencyKey] found in DB
	 */
	@Deprecated
	int countTasks(String processorType, String concurrencyKey, Set<ClusterTaskStatus> statuses);
}