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

package org.apache.shardingsphere.sharding.execute.sql.prepare;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.apache.shardingsphere.underlying.executor.constant.ConnectionMode;
import org.apache.shardingsphere.underlying.executor.engine.InputGroup;
import org.apache.shardingsphere.sharding.execute.sql.StatementExecuteUnit;
import org.apache.shardingsphere.underlying.executor.context.ExecutionUnit;
import org.apache.shardingsphere.underlying.executor.context.SQLUnit;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * SQL execute prepare template.
 */
@RequiredArgsConstructor
public final class SQLExecutePrepareTemplate {
    
    private final int maxConnectionsSizePerQuery;
    
    /**
     * Get execute unit groups.
     * 首先按照数据库进行分组，然后根据 SQL 单元的数量与 maxConnectionsSizePerQuery 计算得出采用连接限制类型还是内存限制类型，最后生成 SQL 执行单元的最后分组
     * @param executionUnits execution units
     * @param callback SQL execute prepare callback
     * @return statement execute unit groups
     * @throws SQLException SQL exception
     */
    public Collection<InputGroup<StatementExecuteUnit>> getExecuteUnitGroups(final Collection<ExecutionUnit> executionUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        return getSynchronizedExecuteUnitGroups(executionUnits, callback);
    }
    // 生成同步执行单元分组
    private Collection<InputGroup<StatementExecuteUnit>> getSynchronizedExecuteUnitGroups(
            final Collection<ExecutionUnit> executionUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        Map<String, List<SQLUnit>> sqlUnitGroups = getSQLUnitGroups(executionUnits); // 按照数据库进行分组，生成数据源与其 SQLUnit 的对应映射
        Collection<InputGroup<StatementExecuteUnit>> result = new LinkedList<>();
        for (Entry<String, List<SQLUnit>> entry : sqlUnitGroups.entrySet()) {
            result.addAll(getSQLExecuteGroups(entry.getKey(), entry.getValue(), callback)); // 将 SQLUnit 集合转化为 InputGroup<StatementExecuteUnit>，对应关系为1:1
        }
        return result;
    }
    // 按照数据库进行分组
    private Map<String, List<SQLUnit>> getSQLUnitGroups(final Collection<ExecutionUnit> executionUnits) {
        Map<String, List<SQLUnit>> result = new LinkedHashMap<>(executionUnits.size(), 1);
        for (ExecutionUnit each : executionUnits) {
            if (!result.containsKey(each.getDataSourceName())) {
                result.put(each.getDataSourceName(), new LinkedList<>());
            }
            result.get(each.getDataSourceName()).add(each.getSqlUnit());
        }
        return result;
    }
    // 根据SQL单元的数量与maxConnectionsSizePerQuery计算得出采用连接限制类型还是内存限制类型
    private List<InputGroup<StatementExecuteUnit>> getSQLExecuteGroups(final String dataSourceName,
                                                                       final List<SQLUnit> sqlUnits, final SQLExecutePrepareCallback callback) throws SQLException {
        List<InputGroup<StatementExecuteUnit>> result = new LinkedList<>();
        int desiredPartitionSize = Math.max(0 == sqlUnits.size() % maxConnectionsSizePerQuery ? sqlUnits.size() / maxConnectionsSizePerQuery : sqlUnits.size() / maxConnectionsSizePerQuery + 1, 1);
        List<List<SQLUnit>> sqlUnitPartitions = Lists.partition(sqlUnits, desiredPartitionSize);
        ConnectionMode connectionMode = maxConnectionsSizePerQuery < sqlUnits.size() ? ConnectionMode.CONNECTION_STRICTLY : ConnectionMode.MEMORY_STRICTLY;
        List<Connection> connections = callback.getConnections(connectionMode, dataSourceName, sqlUnitPartitions.size()); // 根据要执行的 SQL 数量和 maxConnectionsSizePerQuery 配置，计算
        int count = 0;
        for (List<SQLUnit> each : sqlUnitPartitions) {
            result.add(getSQLExecuteGroup(connectionMode, connections.get(count++), dataSourceName, each, callback)); // 根据要执行的SQLUnit，生成对应StatementExecuteUnit对象，添加到返回结果集中
        }
        return result;
    }
    // 生成SQL执行分组
    private InputGroup<StatementExecuteUnit> getSQLExecuteGroup(final ConnectionMode connectionMode, final Connection connection,
                                                                final String dataSourceName, final List<SQLUnit> sqlUnitGroup, final SQLExecutePrepareCallback callback) throws SQLException {
        List<StatementExecuteUnit> result = new LinkedList<>();
        for (SQLUnit each : sqlUnitGroup) {
            result.add(callback.createStatementExecuteUnit(connection, new ExecutionUnit(dataSourceName, each), connectionMode));
        }
        return new InputGroup<>(result);
    }
}
