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

package com.dangdang.ddframe.rdb.sharding.merger.orderby;

import com.dangdang.ddframe.rdb.sharding.merger.util.ResultSetUtil;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.OrderItem;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order by value.
 * 
 * @author zhangliang
 */
@RequiredArgsConstructor
public final class OrderByValue implements Comparable<OrderByValue> {
    // 已排序结果集
    @Getter
    private final ResultSet resultSet;
    // 排序列
    private final List<OrderItem> orderByItems;
    // 排序列对应的值数组。因为一条记录可能有多个排序列，所以是数组
    private List<Comparable<?>> orderValues;
    
    /**
     * iterate next data. 遍历下一个结果集游标
     *
     * @return has next data 是否有下一个结果集
     * @throws SQLException SQL Exception SQL异常
     */
    public boolean next() throws SQLException {
        boolean result = resultSet.next();
        orderValues = result ? getOrderValues() : Collections.<Comparable<?>>emptyList();
        return result;
    }
    // 获得 排序列对应的值数组
    private List<Comparable<?>> getOrderValues() throws SQLException {
        List<Comparable<?>> result = new ArrayList<>(orderByItems.size());
        for (OrderItem each : orderByItems) {
            Object value = resultSet.getObject(each.getIndex());
            Preconditions.checkState(null == value || value instanceof Comparable, "Order by value must implements Comparable");
            result.add((Comparable<?>) value);
        }
        return result;
    }
    //  对比 {@link #orderValues}，即两者的第一条记录
    @Override
    public int compareTo(final OrderByValue o) { //  o 对比 OrderByValue
        for (int i = 0; i < orderByItems.size(); i++) {
            OrderItem thisOrderBy = orderByItems.get(i);
            int result = ResultSetUtil.compareTo(orderValues.get(i), o.orderValues.get(i), thisOrderBy.getType(), thisOrderBy.getNullOrderType());
            if (0 != result) {
                return result;
            }
        }
        return 0;
    }
}
