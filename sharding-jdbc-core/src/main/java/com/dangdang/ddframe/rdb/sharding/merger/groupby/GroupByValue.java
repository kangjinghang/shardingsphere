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

import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.OrderItem;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Group by value.
 * 
 * @author zhangliang
 */
@Getter
@EqualsAndHashCode
public final class GroupByValue {
    // 分组条件值数组
    private final List<?> groupValues;
    
    public GroupByValue(final ResultSet resultSet, final List<OrderItem> groupByItems) throws SQLException {
        groupValues = getGroupByValues(resultSet, groupByItems);
    }
    // 获得分组条件值数组。例如，`GROUP BY user_id, order_status` 返回的某条记录结果为 `userId = 1, order_status = 3`，对应的 `groupValues = [1, 3]`
    private List<?> getGroupByValues(final ResultSet resultSet, final List<OrderItem> groupByItems) throws SQLException {
        List<Object> result = new ArrayList<>(groupByItems.size());
        for (OrderItem each : groupByItems) {
            result.add(resultSet.getObject(each.getIndex())); // 从结果集获得每个分组条件的值
        }
        return result;
    }
}
