/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.shardingjdbc.executor;

import lombok.Getter;
import lombok.Setter;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.encrypt.metadata.EncryptTableMetaDataDecorator;
import org.apache.shardingsphere.core.metadata.ShardingTableMetaDataDecorator;
import org.apache.shardingsphere.core.metadata.ShardingMetaDataLoader;
import org.apache.shardingsphere.sharding.execute.sql.StatementExecuteUnit;
import org.apache.shardingsphere.sharding.execute.sql.execute.SQLExecuteCallback;
import org.apache.shardingsphere.sharding.execute.sql.execute.SQLExecuteTemplate;
import org.apache.shardingsphere.sharding.execute.sql.prepare.SQLExecutePrepareTemplate;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.connection.ShardingConnection;
import org.apache.shardingsphere.shardingjdbc.jdbc.core.context.ShardingRuntimeContext;
import org.apache.shardingsphere.spi.database.type.DatabaseType;
import org.apache.shardingsphere.sql.parser.binder.metadata.index.IndexMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.table.TableMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.binder.statement.ddl.AlterTableStatementContext;
import org.apache.shardingsphere.sql.parser.binder.statement.ddl.CreateIndexStatementContext;
import org.apache.shardingsphere.sql.parser.binder.statement.ddl.CreateTableStatementContext;
import org.apache.shardingsphere.sql.parser.binder.statement.ddl.DropIndexStatementContext;
import org.apache.shardingsphere.sql.parser.binder.statement.ddl.DropTableStatementContext;
import org.apache.shardingsphere.sql.parser.sql.segment.ddl.index.IndexSegment;
import org.apache.shardingsphere.sql.parser.sql.segment.generic.table.SimpleTableSegment;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.AlterTableStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.CreateIndexStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.CreateTableStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.DropIndexStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.DropTableStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationPropertyKey;
import org.apache.shardingsphere.underlying.executor.engine.ExecutorEngine;
import org.apache.shardingsphere.underlying.executor.engine.InputGroup;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Abstract statement executor. 各类StatementExecutor的抽象类，它包含了Statement执行的各种信息，包括resultSetType、resultSetConcurrency、resultSetHoldability，另外也持有ShardingSphere的Connection实例、路由后的真实Statement列表，参数集合、执行后的结果集、执行分组信息、以及执行准备引擎、执行引擎
 */
@Getter
public abstract class AbstractStatementExecutor {
    
    private final DatabaseType databaseType;
    // Statement 执行的各种信息
    private final int resultSetType;
    // Statement 执行的各种信息
    private final int resultSetConcurrency;
    // Statement 执行的各种信息
    private final int resultSetHoldability;
    // ShardingSphere 的 Connection 实例
    private final ShardingConnection connection;
    // 执行准备引擎
    private final SQLExecutePrepareTemplate sqlExecutePrepareTemplate;
    // 执行引擎
    private final SQLExecuteTemplate sqlExecuteTemplate;
    
    private final Collection<Connection> connections = new LinkedList<>();
    // 路由后的【真实】参数集合
    private final List<List<Object>> parameterSets = new LinkedList<>();
    // 路由后的【真实】 Statement 列表
    private final List<Statement> statements = new LinkedList<>();
    // 路由后的【真实】执行后的结果集
    private final List<ResultSet> resultSets = new CopyOnWriteArrayList<>();
    // 执行分组信息
    private final Collection<InputGroup<StatementExecuteUnit>> inputGroups = new LinkedList<>();
    
    @Setter
    private SQLStatementContext sqlStatementContext;
    
    public AbstractStatementExecutor(final int resultSetType, final int resultSetConcurrency, final int resultSetHoldability, final ShardingConnection shardingConnection) {
        this.databaseType = shardingConnection.getRuntimeContext().getDatabaseType();
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
        this.resultSetHoldability = resultSetHoldability;
        this.connection = shardingConnection;
        int maxConnectionsSizePerQuery = connection.getRuntimeContext().getProperties().<Integer>getValue(ConfigurationPropertyKey.MAX_CONNECTIONS_SIZE_PER_QUERY);
        ExecutorEngine executorEngine = connection.getRuntimeContext().getExecutorEngine();
        sqlExecutePrepareTemplate = new SQLExecutePrepareTemplate(maxConnectionsSizePerQuery);
        sqlExecuteTemplate = new SQLExecuteTemplate(executorEngine, connection.isHoldTransaction());
    }
    
    protected final void cacheStatements() {
        for (InputGroup<StatementExecuteUnit> each : inputGroups) {
            statements.addAll(each.getInputs().stream().map(StatementExecuteUnit::getStatement).collect(Collectors.toList()));
            parameterSets.addAll(each.getInputs().stream().map(input -> input.getExecutionUnit().getSqlUnit().getParameters()).collect(Collectors.toList()));
        }
    }
    
    /**
     * To make sure SkyWalking will be available at the next release of ShardingSphere,
     * a new plugin should be provided to SkyWalking project if this API changed.
     * 通过 SQLExecuteTemplate 来真正完成SQL的执行
     * @see <a href="https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md#user-content-plugin-development-guide">Plugin Development Guide</a>
     * 
     * @param executeCallback execute callback
     * @param <T> class type of return value 
     * @return result
     * @throws SQLException SQL exception
     */
    @SuppressWarnings("unchecked")
    protected final <T> List<T> executeCallback(final SQLExecuteCallback<T> executeCallback) throws SQLException {
        List<T> result = sqlExecuteTemplate.execute((Collection) inputGroups, executeCallback); // 通过 sqlExecuteTemplate 对输入的分组对象，执行指定的回调操作
        refreshMetaDataIfNeeded(connection.getRuntimeContext(), sqlStatementContext); // 刷新元数据， 因为DDL会修改元数据信息
        return result;
    }
    
    /**
     * is accumulate.
     * 
     * @return accumulate or not
     */
    public final boolean isAccumulate() {
        return !connection.getRuntimeContext().getRule().isAllBroadcastTables(sqlStatementContext.getTablesContext().getTableNames());
    }
    
    /**
     * Clear data.
     *
     * @throws SQLException SQL exception
     */
    public void clear() throws SQLException {
        clearStatements();
        statements.clear();
        parameterSets.clear();
        connections.clear();
        resultSets.clear();
        inputGroups.clear();
    }
    
    private void clearStatements() throws SQLException {
        for (Statement each : getStatements()) {
            each.close();
        }
    }
    // 刷新元数据， 因为 DDL 会修改元数据信息，表名、列名、字段类型、主键以及索引等信息可能发生了变化
    private void refreshMetaDataIfNeeded(final ShardingRuntimeContext runtimeContext, final SQLStatementContext sqlStatementContext) throws SQLException {
        if (null == sqlStatementContext) {
            return;
        }
        if (sqlStatementContext instanceof CreateTableStatementContext) {
            refreshTableMetaData(runtimeContext, ((CreateTableStatementContext) sqlStatementContext).getSqlStatement());
        } else if (sqlStatementContext instanceof AlterTableStatementContext) {
            refreshTableMetaData(runtimeContext, ((AlterTableStatementContext) sqlStatementContext).getSqlStatement());
        } else if (sqlStatementContext instanceof DropTableStatementContext) {
            refreshTableMetaData(runtimeContext, ((DropTableStatementContext) sqlStatementContext).getSqlStatement());
        } else if (sqlStatementContext instanceof CreateIndexStatementContext) {
            refreshTableMetaData(runtimeContext, ((CreateIndexStatementContext) sqlStatementContext).getSqlStatement());
        } else if (sqlStatementContext instanceof DropIndexStatementContext) {
            refreshTableMetaData(runtimeContext, ((DropIndexStatementContext) sqlStatementContext).getSqlStatement());
        }
    }
    
    private void refreshTableMetaData(final ShardingRuntimeContext runtimeContext, final CreateTableStatement createTableStatement) throws SQLException {
        String tableName = createTableStatement.getTable().getTableName().getIdentifier().getValue();
        runtimeContext.getMetaData().getSchema().put(tableName, loadTableMeta(tableName, databaseType));
    }
    
    private void refreshTableMetaData(final ShardingRuntimeContext runtimeContext, final AlterTableStatement alterTableStatement) throws SQLException {
        String tableName = alterTableStatement.getTable().getTableName().getIdentifier().getValue();
        runtimeContext.getMetaData().getSchema().put(tableName, loadTableMeta(tableName, databaseType));
    }
    
    private void refreshTableMetaData(final ShardingRuntimeContext runtimeContext, final DropTableStatement dropTableStatement) {
        for (SimpleTableSegment each : dropTableStatement.getTables()) {
            runtimeContext.getMetaData().getSchema().remove(each.getTableName().getIdentifier().getValue());
        }
    }
    
    private void refreshTableMetaData(final ShardingRuntimeContext runtimeContext, final CreateIndexStatement createIndexStatement) {
        if (null == createIndexStatement.getIndex()) {
            return;
        }
        String indexName = createIndexStatement.getIndex().getIdentifier().getValue();
        runtimeContext.getMetaData().getSchema().get(createIndexStatement.getTable().getTableName().getIdentifier().getValue()).getIndexes().put(indexName, new IndexMetaData(indexName));
    }
    
    private void refreshTableMetaData(final ShardingRuntimeContext runtimeContext, final DropIndexStatement dropIndexStatement) {
        Collection<String> indexNames = getIndexNames(dropIndexStatement);
        TableMetaData tableMetaData = runtimeContext.getMetaData().getSchema().get(dropIndexStatement.getTable().getTableName().getIdentifier().getValue());
        if (null != dropIndexStatement.getTable()) {
            for (String each : indexNames) {
                tableMetaData.getIndexes().remove(each);
            }
        }
        for (String each : indexNames) {
            if (findLogicTableName(runtimeContext.getMetaData().getSchema(), each).isPresent()) {
                tableMetaData.getIndexes().remove(each);
            }
        }
    }
    
    private Collection<String> getIndexNames(final DropIndexStatement dropIndexStatement) {
        Collection<String> result = new LinkedList<>();
        for (IndexSegment each : dropIndexStatement.getIndexes()) {
            result.add(each.getIdentifier().getValue());
        }
        return result;
    }
    
    private Optional<String> findLogicTableName(final SchemaMetaData schemaMetaData, final String logicIndexName) {
        for (String each : schemaMetaData.getAllTableNames()) {
            if (schemaMetaData.get(each).getIndexes().containsKey(logicIndexName)) {
                return Optional.of(each);
            }
        }
        return Optional.empty();
    }
    
    private TableMetaData loadTableMeta(final String tableName, final DatabaseType databaseType) throws SQLException {
        ShardingRule shardingRule = connection.getRuntimeContext().getRule();
        int maxConnectionsSizePerQuery = connection.getRuntimeContext().getProperties().<Integer>getValue(ConfigurationPropertyKey.MAX_CONNECTIONS_SIZE_PER_QUERY);
        boolean isCheckingMetaData = connection.getRuntimeContext().getProperties().<Boolean>getValue(ConfigurationPropertyKey.CHECK_TABLE_METADATA_ENABLED);
        TableMetaData result = new ShardingMetaDataLoader(connection.getDataSourceMap(), shardingRule, maxConnectionsSizePerQuery, isCheckingMetaData).load(tableName, databaseType);
        result = new ShardingTableMetaDataDecorator().decorate(result, tableName, shardingRule);
        if (!shardingRule.getEncryptRule().getEncryptTableNames().isEmpty()) {
            result = new EncryptTableMetaDataDecorator().decorate(result, tableName, shardingRule.getEncryptRule());
        }
        return result;
    }
}
