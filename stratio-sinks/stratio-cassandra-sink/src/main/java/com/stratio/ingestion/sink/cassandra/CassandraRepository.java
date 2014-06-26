/**
 * Copyright (C) 2014 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stratio.ingestion.sink.cassandra;

import java.util.List;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.base.Strings;

class CassandraRepository {

	private final String table;
	private final String keyspace;
	private final Session session;
	private final Cluster cluster;
	private final String consistencyLevel;
	private ColumnDefinition definition;
	
	private String primaryKey;
	private String keyspaceStatement;
	private String tableStatement;

	protected CassandraRepository() {
		this.table = null;
		this.keyspace = null;
		this.cluster = null;
		this.session = null;
		this.consistencyLevel = "QUORUM";
		this.definition = null;
		this.primaryKey = null;
	}

	public CassandraRepository(String host, String table, String keyspace,
			int port, String clusterName, String consistencyLevel,
			ColumnDefinition definition) {
		this.table = table;
		this.keyspace = keyspace;
		this.cluster = buildCluster(port, clusterName, host);
		this.session = this.cluster.connect();
		this.consistencyLevel = consistencyLevel;
		this.definition = definition;
	}
	
	public TableMetadata createStructure() {
		KeyspaceMetadata keyspaceMetadata = session.getCluster().getMetadata()
				.getKeyspace(keyspace);
		if (keyspaceMetadata == null) {
			createKeyspace();
		}
		TableMetadata tableMetadata = session.getCluster().getMetadata()
				.getKeyspace(keyspace).getTable(table);
		if (tableMetadata == null) {
			createTable();
			tableMetadata = session.getCluster().getMetadata()
					.getKeyspace(keyspace).getTable(table);
		}
		return tableMetadata;
	}

	private void createKeyspace() {
		if (Strings.isNullOrEmpty(keyspaceStatement)) {
			createDefaultKeyspace();
		} else {
			session.execute(keyspaceStatement);
		}
	}

	private void createTable() {
		if (!Strings.isNullOrEmpty(tableStatement)) {
			session.execute(tableStatement);
		} else if (!Strings.isNullOrEmpty(primaryKey) && definition != null){
            createDefaultTable();
		} else {
			throw new CassandraSinkException("The table statement or the primary key and the definition file must be not null");
		}
	}
	
	private void createDefaultKeyspace() {
		session.execute("CREATE KEYSPACE IF NOT EXISTS "
				+ keyspace
				+ " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };");
	}
	
	private void createDefaultTable() {
		StringBuilder columnType = new StringBuilder();
        // Convert HashTable of columns to string
        for (FieldDefinition field : definition.getFields()) {
            columnType.append(field.getColumnName());
            columnType.append(' ');
            columnType.append(field.getCassandraType());
            columnType.append(',');
        }
        String query = String.format("CREATE TABLE IF NOT EXISTS %s.%s (%s PRIMARY KEY (%s));",
                keyspace, table, columnType.toString(), primaryKey);
        session.execute(query);
	}

	public void save(List<CassandraRow> rows) {
		try {
			BatchStatement batch = new BatchStatement();
			for (CassandraRow row : rows) {
				Insert buildInsert = buildInsert(row, this.keyspace,
						this.table);
				batch.add(buildInsert);
			}
			batch.setConsistencyLevel(ConsistencyLevel.valueOf(this.consistencyLevel));
			this.session.execute(batch);
		} catch (Exception e) {
			throw new CassandraSinkException(e);
		}
	}
	
	public void close() {
		this.cluster.closeAsync();
	}

	private static final Cluster buildCluster(int port, String clusterName,
			String... hosts) {
		return Cluster.builder().addContactPoints(hosts).withPort(port)
				.withClusterName(clusterName).build();
	}

	@SuppressWarnings("rawtypes")
	private static final Insert buildInsert(CassandraRow row, String keyspace,
			String table) {
		Insert insert = QueryBuilder.insertInto(keyspace, table);
		for (CassandraField field : row.getFields()) {
			insert.value(field.getColumnName(), field.getValue());
		}
		return insert;
	}

	protected String getTable() {
		return this.table;
	}

	protected String getKeyspace() {
		return this.keyspace;
	}

	protected Session getSession() {
		return this.session;
	}

	protected Cluster getCluster() {
		return this.cluster;
	}
	
	public String getPrimaryKey() {
		return primaryKey;
	}

	public void setPrimaryKey(String primaryKey) {
		this.primaryKey = primaryKey;
	}

	public String getKeyspaceStatement() {
		return keyspaceStatement;
	}

	public void setKeyspaceStatement(String keyspaceStatement) {
		this.keyspaceStatement = keyspaceStatement;
	}

	public String getTableStatement() {
		return tableStatement;
	}

	public void setTableStatement(String tableStatement) {
		this.tableStatement = tableStatement;
	}

}
