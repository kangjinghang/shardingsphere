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

package com.dangdang.ddframe.rdb.sharding.merger.groupby;

import com.dangdang.ddframe.rdb.sharding.merger.groupby.aggregation.AggregationUnit;
import com.dangdang.ddframe.rdb.sharding.merger.groupby.aggregation.AggregationUnitFactory;
import com.dangdang.ddframe.rdb.sharding.merger.orderby.OrderByStreamResultSetMerger;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.selectitem.AggregationSelectItem;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.dql.select.SelectStatement;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Stream merger for group by.
 *
 * @author zhangliang
 */
public final class GroupByStreamResultSetMerger extends OrderByStreamResultSetMerger {
    // 查询列名与位置映射
    private final Map<String, Integer> labelAndIndexMap;
    //  Select SQL语句对象
    private final SelectStatement selectStatement;
    // 当前结果记录
    private final List<Object> currentRow;
    // 下一条结果记录 GROUP BY 条件
    private List<?> currentGroupByValues;
    
    public GroupByStreamResultSetMerger(
            final Map<String, Integer> labelAndIndexMap, final List<ResultSet> resultSets, final SelectStatement selectStatement) throws SQLException {
        super(resultSets, selectStatement.getOrderByItems());
        this.labelAndIndexMap = labelAndIndexMap;
        this.selectStatement = selectStatement;
        currentRow = new ArrayList<>(labelAndIndexMap.size());
        currentGroupByValues = getOrderByValuesQueue().isEmpty() ? Collections.emptyList() : new GroupByValue(getCurrentResultSet(), selectStatement.getGroupByItems()).getGroupValues(); // 初始化下一条结果记录 GROUP BY 条件
    }
    
    @Override
    public boolean next() throws SQLException {
        currentRow.clear(); // 清除当前结果记录
        if (getOrderByValuesQueue().isEmpty()) {
            return false;
        }
        if (isFirstNext()) {
            super.next();
        }
        if (aggregateCurrentGroupByRowAndNext()) { // 顺序合并下面相同分组条件的记录
            currentGroupByValues = new GroupByValue(getCurrentResultSet(), selectStatement.getGroupByItems()).getGroupValues(); // 生成下一条结果记录 GROUP BY 条件
        }
        return true;
    }
    
    private boolean aggregateCurrentGroupByRowAndNext() throws SQLException {
        boolean result = false;
        Map<AggregationSelectItem, AggregationUnit> aggregationUnitMap = Maps.toMap(selectStatement.getAggregationSelectItems(), new Function<AggregationSelectItem, AggregationUnit>() {
            
            @Override
            public AggregationUnit apply(final AggregationSelectItem input) {
                return AggregationUnitFactory.create(input.getType());
            }
        });  // 生成计算单元
        while (currentGroupByValues.equals(new GroupByValue(getCurrentResultSet(), selectStatement.getGroupByItems()).getGroupValues())) { // 循环顺序合并下面相同分组条件的记录
            aggregate(aggregationUnitMap); // 归并聚合值
            cacheCurrentRow(); // 缓存当前记录到结果记录
            result = super.next(); // 获取下一条记录
            if (!result) {
                break;
            }
        }
        setAggregationValueToCurrentRow(aggregationUnitMap); // 设置当前记录的聚合字段结果
        return result;
    }
    
    private void aggregate(final Map<AggregationSelectItem, AggregationUnit> aggregationUnitMap) throws SQLException {
        for (Entry<AggregationSelectItem, AggregationUnit> entry : aggregationUnitMap.entrySet()) {
            List<Comparable<?>> values = new ArrayList<>(2);
            if (entry.getKey().getDerivedAggregationSelectItems().isEmpty()) {  // SUM/COUNT/MAX/MIN 聚合列
                values.add(getAggregationValue(entry.getKey()));
            } else {
                for (AggregationSelectItem each : entry.getKey().getDerivedAggregationSelectItems()) {  // AVG 聚合列
                    values.add(getAggregationValue(each));
                }
            }
            entry.getValue().merge(values);
        }
    }
    
    private void cacheCurrentRow() throws SQLException {
        for (int i = 0; i < getCurrentResultSet().getMetaData().getColumnCount(); i++) {
            currentRow.add(getCurrentResultSet().getObject(i + 1));
        }
    }
    
    private Comparable<?> getAggregationValue(final AggregationSelectItem aggregationSelectItem) throws SQLException {
        Object result = getCurrentResultSet().getObject(aggregationSelectItem.getIndex());
        Preconditions.checkState(null == result || result instanceof Comparable, "Aggregation value must implements Comparable");
        return (Comparable<?>) result;
    }
    
    private void setAggregationValueToCurrentRow(final Map<AggregationSelectItem, AggregationUnit> aggregationUnitMap) {
        for (Entry<AggregationSelectItem, AggregationUnit> entry : aggregationUnitMap.entrySet()) {
            currentRow.set(entry.getKey().getIndex() - 1, entry.getValue().getResult());  // 获取计算结果
        }
    }
    
    @Override
    public Object getValue(final int columnIndex, final Class<?> type) throws SQLException {
        return currentRow.get(columnIndex - 1);
    }
    
    @Override
    public Object getValue(final String columnLabel, final Class<?> type) throws SQLException {
        Preconditions.checkState(labelAndIndexMap.containsKey(columnLabel), String.format("Can't find columnLabel: %s", columnLabel));
        return currentRow.get(labelAndIndexMap.get(columnLabel) - 1);
    }
    
    @Override
    public Object getCalendarValue(final int columnIndex, final Class<?> type, final Calendar calendar) throws SQLException {
        return currentRow.get(columnIndex - 1);
    }
    
    @Override
    public Object getCalendarValue(final String columnLabel, final Class<?> type, final Calendar calendar) throws SQLException {
        Preconditions.checkState(labelAndIndexMap.containsKey(columnLabel), String.format("Can't find columnLabel: %s", columnLabel));
        return currentRow.get(labelAndIndexMap.get(columnLabel) - 1);
    }
}
