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

package com.dangdang.ddframe.rdb.sharding.merger.limit;

import com.dangdang.ddframe.rdb.sharding.merger.ResultSetMerger;
import com.dangdang.ddframe.rdb.sharding.merger.common.AbstractDecoratorResultSetMerger;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.limit.Limit;

import java.sql.SQLException;

/**
 * Decorator merger for limit. 基于 Decorator 分页结果集归并实现
 *
 * @author zhangliang
 */
public final class LimitDecoratorResultSetMerger extends AbstractDecoratorResultSetMerger {
    // 分页条件
    private final Limit limit;
    // 是否全部记录都跳过了，即无符合条件记录
    private final boolean skipAll;
    // 当前已返回行数
    private int rowNumber;
    
    public LimitDecoratorResultSetMerger(final ResultSetMerger resultSetMerger, final Limit limit) throws SQLException {
        super(resultSetMerger);
        this.limit = limit;
        skipAll = skipOffset();
    }
    
    private boolean skipOffset() throws SQLException {
        for (int i = 0; i < limit.getOffsetValue(); i++) { // 跳过 skip 记录
            if (!getResultSetMerger().next()) {
                return true;
            }
        }
        rowNumber = limit.isRowCountRewriteFlag() ? 0 : limit.getOffsetValue(); // 行数
        return false;
    }
    
    @Override
    public boolean next() throws SQLException {
        if (skipAll) {
            return false;
        }
        if (limit.getRowCountValue() > -1) { // 获得下一条记录
            return ++rowNumber <= limit.getRowCountValue() && getResultSetMerger().next();
        }
        return getResultSetMerger().next(); // 部分db 可以直 offset，不写 limit 行数，例如 oracle
    }
}
