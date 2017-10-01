package com.microfocus.octane.cluster.tasks.impl;

import com.microfocus.octane.cluster.tasks.api.ClusterTasksServiceConfigurerSPI;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
class ClusterTasksServiceSchemaManager {
	private final Logger logger = LoggerFactory.getLogger(ClusterTasksServiceSchemaManager.class);

	private static final String CTS_SCHEMA_HISTORY_TABLE_NAME = "CTS_SCHEMA_HISTORY";
	private static final String SQL_MIGRATION_PREFIX = "v";

	boolean executeSchemaMaintenance(ClusterTasksServiceConfigurerSPI.DBType dbType, DataSource dataSource) {
		boolean result = true;
		if (dbType == null) {
			logger.error("DB type MUST NOT be null, schema maintenance won't run");
			result = false;
		} else if (dataSource == null) {
			logger.error("DataSource MUST NOT be null, schema maintenance won't run");
			result = false;
		} else {
			Flyway flyway = new Flyway();
			try {
				flyway.setDataSource(dataSource);
				flyway.setTable(CTS_SCHEMA_HISTORY_TABLE_NAME);
				flyway.setSqlMigrationPrefix(SQL_MIGRATION_PREFIX);
				flyway.setBaselineOnMigrate(true);
				flyway.setLocations(getSQLsLocation(dbType));
				flyway.migrate();
			} catch (Exception me) {
				logger.error("DB maintenance failed, attempting repair", me);
				try {
					flyway.repair();
					logger.error("DB repair after migration failure has SUCCEED", me);
				} catch (Exception re) {
					logger.error("DB repair after migration failure has FAILED", re);
				}
				result = false;
			}
		}
		return result;
	}

	private String getSQLsLocation(ClusterTasksServiceConfigurerSPI.DBType dbType) {
		String result = null;
		switch (dbType) {
			case ORACLE:
				result = "classpath:cts/schema/oracle";
				break;
			case MSSQL:
				result = "classpath:cts/schema/sqlserver";
				break;
		}
		return result;
	}
}