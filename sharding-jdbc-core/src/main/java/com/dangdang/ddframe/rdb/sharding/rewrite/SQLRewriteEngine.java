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

package com.dangdang.ddframe.rdb.sharding.rewrite;


import com.dangdang.ddframe.rdb.sharding.api.rule.BindingTableRule;
import com.dangdang.ddframe.rdb.sharding.api.rule.ShardingRule;
import com.dangdang.ddframe.rdb.sharding.parsing.lexer.token.DefaultKeyword;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.OrderItem;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.limit.Limit;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.SQLStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dql.select.SelectStatement;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.ItemsToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.OffsetToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.OrderByToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.RowCountToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.SQLToken;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.TableToken;
import com.dangdang.ddframe.rdb.sharding.routing.type.TableUnit;
import com.dangdang.ddframe.rdb.sharding.routing.type.complex.CartesianTableReference;
import com.google.common.base.Optional;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SQL rewrite engine.
 * 
 * <p>Rewrite logic SQL to actual SQL, should rewrite table name and optimize something.</p>
 *
 * @author zhangliang
 */
public final class SQLRewriteEngine {
    
    private final ShardingRule shardingRule;
    
    private final String originalSQL;
    
    private final List<SQLToken> sqlTokens = new LinkedList<>();
    
    private final SQLStatement sqlStatement;
    
    /**
     * Constructs SQL rewrite engine.
     * 
     * @param shardingRule databases and tables sharding rule
     * @param originalSQL original SQL
     * @param sqlStatement SQL statement
     */
    public SQLRewriteEngine(final ShardingRule shardingRule, final String originalSQL, final SQLStatement sqlStatement) {
        this.shardingRule = shardingRule;
        this.originalSQL = originalSQL;
        this.sqlStatement = sqlStatement;
        sqlTokens.addAll(sqlStatement.getSqlTokens());
    }
    
    /**
     * rewrite SQL. SQL改写
     *
     * @param isRewriteLimit is rewrite limit 是否重写Limit
     * @return SQL builder
     */
    public SQLBuilder rewrite(final boolean isRewriteLimit) {
        SQLBuilder result = new SQLBuilder();
        if (sqlTokens.isEmpty()) {
            result.appendLiterals(originalSQL);
            return result;
        }
        int count = 0;
        sortByBeginPosition(); // 排序SQLToken，按照 beginPosition 递增
        for (SQLToken each : sqlTokens) {
            if (0 == count) {
                result.appendLiterals(originalSQL.substring(0, each.getBeginPosition()));
            }
            if (each instanceof TableToken) { // 拼接每个 SQLToken
                appendTableToken(result, (TableToken) each, count, sqlTokens);
            } else if (each instanceof ItemsToken) {
                appendItemsToken(result, (ItemsToken) each, count, sqlTokens);
            } else if (each instanceof RowCountToken) {
                appendLimitRowCount(result, (RowCountToken) each, count, sqlTokens, isRewriteLimit);
            } else if (each instanceof OffsetToken) {
                appendLimitOffsetToken(result, (OffsetToken) each, count, sqlTokens, isRewriteLimit);
            } else if (each instanceof OrderByToken) {
                appendOrderByToken(result, count, sqlTokens);
            }
            count++;
        }
        return result;
    }
    
    private void sortByBeginPosition() {
        Collections.sort(sqlTokens, new Comparator<SQLToken>() {
            
            @Override
            public int compare(final SQLToken o1, final SQLToken o2) {
                return o1.getBeginPosition() - o2.getBeginPosition();
            }
        });
    }
    // 拼接 TableToken
    private void appendTableToken(final SQLBuilder sqlBuilder, final TableToken tableToken, final int count, final List<SQLToken> sqlTokens) {
        String tableName = sqlStatement.getTables().getTableNames().contains(tableToken.getTableName()) ? tableToken.getTableName() : tableToken.getOriginalLiterals(); // 拼接 TableToken
        sqlBuilder.appendTable(tableName);
        int beginPosition = tableToken.getBeginPosition() + tableToken.getOriginalLiterals().length(); // 拼接 SQLToken 后面的字符串
        appendRest(sqlBuilder, count, sqlTokens, beginPosition);
    }
    // 拼接 TableToken
    private void appendItemsToken(final SQLBuilder sqlBuilder, final ItemsToken itemsToken, final int count, final List<SQLToken> sqlTokens) {
        for (String item : itemsToken.getItems()) { // 拼接 ItemsToken
            sqlBuilder.appendLiterals(", ");
            sqlBuilder.appendLiterals(item);
        }
        int beginPosition = itemsToken.getBeginPosition(); // SQLToken 后面的字符串
        appendRest(sqlBuilder, count, sqlTokens, beginPosition);
    }
    
    private void appendLimitRowCount(final SQLBuilder sqlBuilder, final RowCountToken rowCountToken, final int count, final List<SQLToken> sqlTokens, final boolean isRewrite) {
        SelectStatement selectStatement = (SelectStatement) sqlStatement;
        Limit limit = selectStatement.getLimit();
        if (!isRewrite) {  // 路由结果为单分片
            sqlBuilder.appendLiterals(String.valueOf(rowCountToken.getRowCount()));
        } else if ((!selectStatement.getGroupByItems().isEmpty() || !selectStatement.getAggregationSelectItems().isEmpty()) && !selectStatement.isSameGroupByAndOrderByItems()) {
            sqlBuilder.appendLiterals(String.valueOf(Integer.MAX_VALUE));
        } else {  // 路由结果为多分片
            sqlBuilder.appendLiterals(String.valueOf(limit.isRowCountRewriteFlag() ? rowCountToken.getRowCount() + limit.getOffsetValue() : rowCountToken.getRowCount()));
        }
        int beginPosition = rowCountToken.getBeginPosition() + String.valueOf(rowCountToken.getRowCount()).length(); // SQLToken 后面的字符串
        appendRest(sqlBuilder, count, sqlTokens, beginPosition);
    }
    // 拼接 OffsetToken
    private void appendLimitOffsetToken(final SQLBuilder sqlBuilder, final OffsetToken offsetToken, final int count, final List<SQLToken> sqlTokens, final boolean isRewrite) {
        sqlBuilder.appendLiterals(isRewrite ? "0" : String.valueOf(offsetToken.getOffset())); // 拼接 OffsetToken
        int beginPosition = offsetToken.getBeginPosition() + String.valueOf(offsetToken.getOffset()).length(); // SQLToken 后面的字符串
        appendRest(sqlBuilder, count, sqlTokens, beginPosition);
    }
    // 拼接 OrderByToken
    private void appendOrderByToken(final SQLBuilder sqlBuilder, final int count, final List<SQLToken> sqlTokens) {
        SelectStatement selectStatement = (SelectStatement) sqlStatement;
        StringBuilder orderByLiterals = new StringBuilder();
        orderByLiterals.append(" ").append(DefaultKeyword.ORDER).append(" ").append(DefaultKeyword.BY).append(" "); // 拼接 OrderByToken
        int i = 0;
        for (OrderItem each : selectStatement.getOrderByItems()) {
            if (0 == i) {
                orderByLiterals.append(each.getColumnLabel()).append(" ").append(each.getType().name());
            } else {
                orderByLiterals.append(",").append(each.getColumnLabel()).append(" ").append(each.getType().name());
            }
            i++;
        }
        orderByLiterals.append(" ");
        sqlBuilder.appendLiterals(orderByLiterals.toString());
        int beginPosition = ((SelectStatement) sqlStatement).getGroupByLastPosition();
        appendRest(sqlBuilder, count, sqlTokens, beginPosition);
    }
    
    private void appendRest(final SQLBuilder sqlBuilder, final int count, final List<SQLToken> sqlTokens, final int beginPosition) {
        int endPosition = sqlTokens.size() - 1 == count ? originalSQL.length() : sqlTokens.get(count + 1).getBeginPosition();
        sqlBuilder.appendLiterals(originalSQL.substring(beginPosition, endPosition));
    }
    
    /**
     * Generate SQL string.
     * 
     * @param tableUnit route table unit
     * @param sqlBuilder SQL builder
     * @return SQL string
     */
    public String generateSQL(final TableUnit tableUnit, final SQLBuilder sqlBuilder) {
        return sqlBuilder.toSQL(getTableTokens(tableUnit));
    }
    
    /**
     * Generate SQL string. 生成SQL语句。对于笛卡尔积路由结果和简单路由结果两种情况，处理上大体是一致的：1. 获得 SQL 相关逻辑表对应的真实表映射，2. 根据映射改写 SQL 相关逻辑表为真实表。
     *
     * @param cartesianTableReference cartesian table reference 笛卡尔积路由表单元
     * @param sqlBuilder SQL builder SQL构建器
     * @return SQL string SQL语句
     */
    public String generateSQL(final CartesianTableReference cartesianTableReference, final SQLBuilder sqlBuilder) {
        return sqlBuilder.toSQL(getTableTokens(cartesianTableReference));
    }
    // 获得（笛卡尔积表路由组里的路由表单元逻辑表 和 与其互为BindingTable关系的逻辑表）对应的真实表映射（逻辑表需要在 SQL 中存在）
    private Map<String, String> getTableTokens(final TableUnit tableUnit) {
        Map<String, String> tableTokens = new HashMap<>();
        tableTokens.put(tableUnit.getLogicTableName(), tableUnit.getActualTableName());
        Optional<BindingTableRule> bindingTableRule = shardingRule.findBindingTableRule(tableUnit.getLogicTableName()); // 查找 BindingTableRule
        if (bindingTableRule.isPresent()) {
            tableTokens.putAll(getBindingTableTokens(tableUnit, bindingTableRule.get()));
        }
        return tableTokens;
    }
    
    private Map<String, String> getTableTokens(final CartesianTableReference cartesianTableReference) {
        Map<String, String> tableTokens = new HashMap<>();
        for (TableUnit each : cartesianTableReference.getTableUnits()) {
            tableTokens.put(each.getLogicTableName(), each.getActualTableName());
            Optional<BindingTableRule> bindingTableRule = shardingRule.findBindingTableRule(each.getLogicTableName());
            if (bindingTableRule.isPresent()) {
                tableTokens.putAll(getBindingTableTokens(each, bindingTableRule.get()));
            }
        }
        return tableTokens;
    }
    // 获得 BindingTable 关系的逻辑表对应的真实表映射（逻辑表需要在 SQL 中存在）
    private Map<String, String> getBindingTableTokens(final TableUnit tableUnit, final BindingTableRule bindingTableRule) {
        Map<String, String> result = new HashMap<>();
        for (String eachTable : sqlStatement.getTables().getTableNames()) {
            if (!eachTable.equalsIgnoreCase(tableUnit.getLogicTableName()) && bindingTableRule.hasLogicTable(eachTable)) {
                result.put(eachTable, bindingTableRule.getBindingActualTable(tableUnit.getDataSourceName(), eachTable, tableUnit.getActualTableName()));
            }
        }
        return result;
    }
}
