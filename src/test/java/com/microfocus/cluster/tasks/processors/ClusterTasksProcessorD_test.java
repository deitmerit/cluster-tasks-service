package com.microfocus.cluster.tasks.processors;

import com.microfocus.cluster.tasks.api.ClusterTasksProcessorSimple;
import com.microfocus.cluster.tasks.api.dto.ClusterTask;
import com.microfocus.cluster.tasks.api.enums.ClusterTasksDataProviderType;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by gullery on 02/06/2016
 */

public class ClusterTasksProcessorD_test extends ClusterTasksProcessorSimple {
	public final Map<String, Timestamp> tasksProcessed = new LinkedHashMap<>();

	protected ClusterTasksProcessorD_test() {
		super(ClusterTasksDataProviderType.DB, 3, 7000);
	}

	@Override
	public void processTask(ClusterTask task) {
		tasksProcessed.put(task.getBody(), new Timestamp(System.currentTimeMillis()));
	}
}
