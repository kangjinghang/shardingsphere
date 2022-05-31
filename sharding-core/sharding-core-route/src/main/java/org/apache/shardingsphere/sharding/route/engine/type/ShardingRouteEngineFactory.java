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

package org.apache.shardingsphere.sharding.route.engine.type;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.sharding.route.engine.condition.ShardingConditions;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingDataSourceGroupBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingDatabaseBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingMasterInstanceBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.broadcast.ShardingTableBroadcastRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.complex.ShardingComplexRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.defaultdb.ShardingDefaultDatabaseRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.ignore.ShardingIgnoreRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.standard.ShardingStandardRoutingEngine;
import org.apache.shardingsphere.sharding.route.engine.type.unicast.ShardingUnicastRoutingEngine;
import org.apache.shardingsphere.sql.parser.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.sql.parser.sql.statement.SQLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.DALStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.SetStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.ShowDatabasesStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.mysql.UseStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dal.dialect.postgresql.ResetParameterStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dcl.DCLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.ddl.DDLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.DMLStatement;
import org.apache.shardingsphere.sql.parser.sql.statement.dml.SelectStatement;
import org.apache.shardingsphere.sql.parser.binder.type.TableAvailable;
import org.apache.shardingsphere.sql.parser.sql.statement.tcl.TCLStatement;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;

import java.util.Collection;

/**
 * Sharding routing engine factory. ShardingRouteEngine的工厂类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ShardingRouteEngineFactory {
    
    /**
     * Create new instance of routing engine. 根据SQL类型创建不同ShardingRouteEngine，因为不同的类型的SQL对应着的不同的路由策略，例如全库路由、全库表路由、单库路由、标准路由等
     *
     * @param shardingRule sharding rule
     * @param metaData meta data of ShardingSphere
     * @param sqlStatementContext SQL statement context
     * @param shardingConditions shardingConditions
     * @param properties sharding sphere properties
     * @return new instance of routing engine
     */
    public static ShardingRouteEngine newInstance(final ShardingRule shardingRule,
                                                  final ShardingSphereMetaData metaData, final SQLStatementContext sqlStatementContext,
                                                  final ShardingConditions shardingConditions, final ConfigurationProperties properties) {
        SQLStatement sqlStatement = sqlStatementContext.getSqlStatement();
        Collection<String> tableNames = sqlStatementContext.getTablesContext().getTableNames();
        if (sqlStatement instanceof TCLStatement) { // 事务控制类SQL(commit、rollback、savepoint、set transaction)，库广播类路由
            return new ShardingDatabaseBroadcastRoutingEngine();
        }
        if (sqlStatement instanceof DDLStatement) { // DDL SQL（create、alter、drop、truncate...），表广播类路由
            return new ShardingTableBroadcastRoutingEngine(metaData.getSchema(), sqlStatementContext);
        }
        if (sqlStatement instanceof DALStatement) { // DAL SQL (show database、show tables... )，根据SQL类型选择库广播、表路由或者默认库路由
            return getDALRoutingEngine(shardingRule, sqlStatement, tableNames);
        }
        if (sqlStatement instanceof DCLStatement) { // DCL 采用表广播路由或者主库路由
            return getDCLRoutingEngine(sqlStatementContext, metaData);
        }
        if (shardingRule.isAllInDefaultDataSource(tableNames)) { // 如果都是表名都配置默认数据源，则采用默认库路由
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        if (shardingRule.isAllBroadcastTables(tableNames)) { // 如果都属于配置中的广播表，查询采用单一路由，随机选择配置的数据源
            return sqlStatement instanceof SelectStatement ? new ShardingUnicastRoutingEngine(tableNames) : new ShardingDatabaseBroadcastRoutingEngine();
        }
        if (sqlStatementContext.getSqlStatement() instanceof DMLStatement && tableNames.isEmpty() && shardingRule.hasDefaultDataSourceName()) { // DML SQL 如果表名为空而且配置了默认库，则采用默认库路由
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        if (sqlStatementContext.getSqlStatement() instanceof DMLStatement && shardingConditions.isAlwaysFalse() || tableNames.isEmpty() || !shardingRule.tableRuleExists(tableNames)) { // DML SQL如果表名为空或者未配置sharding规则，则采用单一路由，随机选择数据源
            return new ShardingUnicastRoutingEngine(tableNames);
        }
        return getShardingRoutingEngine(shardingRule, sqlStatementContext, shardingConditions, tableNames, properties); // 其它采用标准路由或者复杂路由
    }
    
    private static ShardingRouteEngine getDALRoutingEngine(final ShardingRule shardingRule, final SQLStatement sqlStatement, final Collection<String> tableNames) {
        if (sqlStatement instanceof UseStatement) { // Use SQL忽略类路由
            return new ShardingIgnoreRoutingEngine();
        }
        if (sqlStatement instanceof SetStatement || sqlStatement instanceof ResetParameterStatement || sqlStatement instanceof ShowDatabasesStatement) { // set、reset、show database 库广播类路由
            return new ShardingDatabaseBroadcastRoutingEngine();
        }
        if (!tableNames.isEmpty() && !shardingRule.tableRuleExists(tableNames) && shardingRule.hasDefaultDataSourceName()) { // 如果表名在sharding规则中未配置的使用默认库路由
            return new ShardingDefaultDatabaseRoutingEngine(tableNames);
        }
        if (!tableNames.isEmpty()) { // 如果表名不为空，采用单一路由
            return new ShardingUnicastRoutingEngine(tableNames);
        }
        return new ShardingDataSourceGroupBroadcastRoutingEngine(); // 采用数据库群组路由
    }
    
    private static ShardingRouteEngine getDCLRoutingEngine(final SQLStatementContext sqlStatementContext, final ShardingSphereMetaData metaData) {
        return isDCLForSingleTable(sqlStatementContext) 
                ? new ShardingTableBroadcastRoutingEngine(metaData.getSchema(), sqlStatementContext) : new ShardingMasterInstanceBroadcastRoutingEngine(metaData.getDataSources());
    }
    
    private static boolean isDCLForSingleTable(final SQLStatementContext sqlStatementContext) {
        if (sqlStatementContext instanceof TableAvailable) {
            TableAvailable tableSegmentsAvailable = (TableAvailable) sqlStatementContext;
            return 1 == tableSegmentsAvailable.getAllTables().size() && !"*".equals(tableSegmentsAvailable.getAllTables().iterator().next().getTableName().getIdentifier().getValue());
        }
        return false;
    }
    
    private static ShardingRouteEngine getShardingRoutingEngine(final ShardingRule shardingRule, final SQLStatementContext sqlStatementContext,
                                                                final ShardingConditions shardingConditions, final Collection<String> tableNames, final ConfigurationProperties properties) {
        Collection<String> shardingTableNames = shardingRule.getShardingLogicTableNames(tableNames);
        if (1 == shardingTableNames.size() || shardingRule.isAllBindingTables(shardingTableNames)) { // 只有一张逻辑表或者都是绑定表，采用标准路由
            return new ShardingStandardRoutingEngine(shardingTableNames.iterator().next(), sqlStatementContext, shardingConditions, properties);
        }
        // TODO config for cartesian set
        return new ShardingComplexRoutingEngine(tableNames, sqlStatementContext, shardingConditions, properties);
    }
}
