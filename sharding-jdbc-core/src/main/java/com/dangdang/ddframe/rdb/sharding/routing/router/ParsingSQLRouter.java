/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.routing.router;

import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.constant.DatabaseType;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.ShardingContext;
import com.dangdang.ddframe.rdb.sharding.parsing.SQLParsingEngine;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.GeneratedKey;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.SQLStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dml.insert.InsertStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dql.select.SelectStatement;
import com.dangdang.ddframe.rdb.sharding.rewrite.SQLBuilder;
import com.dangdang.ddframe.rdb.sharding.rewrite.SQLRewriteEngine;
import com.dangdang.ddframe.rdb.sharding.routing.SQLExecutionUnit;
import com.dangdang.ddframe.rdb.sharding.routing.SQLRouteResult;
import com.dangdang.ddframe.rdb.sharding.routing.type.RoutingEngine;
import com.dangdang.ddframe.rdb.sharding.routing.type.RoutingResult;
import com.dangdang.ddframe.rdb.sharding.routing.type.TableUnit;
import com.dangdang.ddframe.rdb.sharding.routing.type.complex.CartesianDataSource;
import com.dangdang.ddframe.rdb.sharding.routing.type.complex.CartesianRoutingResult;
import com.dangdang.ddframe.rdb.sharding.routing.type.complex.CartesianTableReference;
import com.dangdang.ddframe.rdb.sharding.routing.type.complex.ComplexRoutingEngine;
import com.dangdang.ddframe.rdb.sharding.routing.type.simple.SimpleRoutingEngine;
import com.dangdang.ddframe.rdb.sharding.util.SQLLogger;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * SQL router with parse. 需要解析的SQL路由器
 * 
 * @author zhangiang
 */
public final class ParsingSQLRouter implements SQLRouter {
    
    private final ShardingRule shardingRule;
    
    private final DatabaseType databaseType;
    
    private final boolean showSQL;
    
    private final List<Number> generatedKeys;
    
    public ParsingSQLRouter(final ShardingContext shardingContext) {
        shardingRule = shardingContext.getShardingRule();
        databaseType = shardingContext.getDatabaseType();
        showSQL = shardingContext.isShowSQL();
        generatedKeys = new LinkedList<>();
    }
    
    @Override
    public SQLStatement parse(final String logicSQL, final int parametersSize) {
        SQLParsingEngine parsingEngine = new SQLParsingEngine(databaseType, logicSQL, shardingRule);
        SQLStatement result = parsingEngine.parse(); // 使用 SQLParsingEngine 解析SQL
        if (result instanceof InsertStatement) { // 处理 GenerateKeyToken
            ((InsertStatement) result).appendGenerateKeyToken(shardingRule, parametersSize);
        }
        return result;
    }
    
    @Override
    public SQLRouteResult route(final String logicSQL, final List<Object> parameters, final SQLStatement sqlStatement) {
        SQLRouteResult result = new SQLRouteResult(sqlStatement);
        if (sqlStatement instanceof InsertStatement && null != ((InsertStatement) sqlStatement).getGeneratedKey()) { // 处理 插入SQL 主键字段
            processGeneratedKey(parameters, (InsertStatement) sqlStatement, result);
        }
        RoutingResult routingResult = route(parameters, sqlStatement); // 路由
        SQLRewriteEngine rewriteEngine = new SQLRewriteEngine(shardingRule, logicSQL, sqlStatement);  // SQL重写引擎
        boolean isSingleRouting = routingResult.isSingleRouting();
        if (sqlStatement instanceof SelectStatement && null != ((SelectStatement) sqlStatement).getLimit()) { // 处理分页
            processLimit(parameters, (SelectStatement) sqlStatement, isSingleRouting);
        }
        SQLBuilder sqlBuilder = rewriteEngine.rewrite(!isSingleRouting); // SQL 重写
        if (routingResult instanceof CartesianRoutingResult) { // 生成 ExecutionUnit
            for (CartesianDataSource cartesianDataSource : ((CartesianRoutingResult) routingResult).getRoutingDataSources()) {
                for (CartesianTableReference cartesianTableReference : cartesianDataSource.getRoutingTableReferences()) {
                    result.getExecutionUnits().add(new SQLExecutionUnit(cartesianDataSource.getDataSource(), rewriteEngine.generateSQL(cartesianTableReference, sqlBuilder)));  // 生成 SQL
                }
            }
        } else {
            for (TableUnit each : routingResult.getTableUnits().getTableUnits()) {
                result.getExecutionUnits().add(new SQLExecutionUnit(each.getDataSourceName(), rewriteEngine.generateSQL(each, sqlBuilder)));  // 生成 SQL
            }
        }
        if (showSQL) { // 打印 SQL
            SQLLogger.logSQL(logicSQL, sqlStatement, result.getExecutionUnits(), parameters);
        }
        return result;
    }
    
    private RoutingResult route(final List<Object> parameters, final SQLStatement sqlStatement) {
        Collection<String> tableNames = sqlStatement.getTables().getTableNames();
        RoutingEngine routingEngine; // 会根据表情况使用 SimpleRoutingEngine 或 CartesianRoutingEngine 进行路由
        if (1 == tableNames.size() || shardingRule.isAllBindingTables(tableNames) || shardingRule.isAllInDefaultDataSource(tableNames)) {
            routingEngine = new SimpleRoutingEngine(shardingRule, parameters, tableNames.iterator().next(), sqlStatement);
        } else {
            // TODO config for cartesian set
            routingEngine = new ComplexRoutingEngine(shardingRule, parameters, tableNames, sqlStatement);
        }
        return routingEngine.route();
    }
    // 处理 插入SQL 主键字段
    private void processGeneratedKey(final List<Object> parameters, final InsertStatement insertStatement, final SQLRouteResult sqlRouteResult) {
        GeneratedKey generatedKey = insertStatement.getGeneratedKey();
        if (parameters.isEmpty()) { // 已有主键，无占位符，INSERT INTO t_order(order_id, user_id) VALUES (1, 100);
            sqlRouteResult.getGeneratedKeys().add(generatedKey.getValue());
        } else if (parameters.size() == generatedKey.getIndex()) { // 主键字段不存在存在，INSERT INTO t_order(user_id) VALUES(?);
            Number key = shardingRule.generateKey(insertStatement.getTables().getSingleTableName());
            parameters.add(key);
            setGeneratedKeys(sqlRouteResult, key);
        } else if (-1 != generatedKey.getIndex()) {  // 主键字段存在，INSERT INTO t_order(order_id, user_id) VALUES(?, ?);
            setGeneratedKeys(sqlRouteResult, (Number) parameters.get(generatedKey.getIndex()));
        }
    }
    // 设置 主键编号 到 SQL路由结果
    private void setGeneratedKeys(final SQLRouteResult sqlRouteResult, final Number generatedKey) {
        generatedKeys.add(generatedKey);
        sqlRouteResult.getGeneratedKeys().clear();
        sqlRouteResult.getGeneratedKeys().addAll(generatedKeys);
    }
    // 处理分页条件
    private void processLimit(final List<Object> parameters, final SelectStatement selectStatement, final boolean isSingleRouting) {
        if (isSingleRouting) {
            selectStatement.setLimit(null);
            return;
        }
        boolean isNeedFetchAll = (!selectStatement.getGroupByItems().isEmpty() || !selectStatement.getAggregationSelectItems().isEmpty()) && !selectStatement.isSameGroupByAndOrderByItems();
        selectStatement.getLimit().processParameters(parameters, isNeedFetchAll);
    }
}
