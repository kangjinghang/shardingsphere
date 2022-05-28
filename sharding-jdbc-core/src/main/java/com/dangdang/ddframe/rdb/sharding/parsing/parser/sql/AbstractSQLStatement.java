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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.sql;

import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.condition.Conditions;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.context.table.Tables;
import com.dangdang.ddframe.rdb.sharding.parsing.parser.token.SQLToken;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedList;
import java.util.List;

/**
 * SQL statement abstract class.
 *
 * @author zhangliang
 */
@RequiredArgsConstructor
@Getter
@Setter
@ToString
public abstract class AbstractSQLStatement implements SQLStatement {
    // SQL 类型
    private final SQLType type;
    // 表
    private final Tables tables = new Tables();
    // 过滤条件。只有对路由结果有影响的条件，才添加进数组
    private final Conditions conditions = new Conditions();
    // SQL标记对象
    private final List<SQLToken> sqlTokens = new LinkedList<>();
    
    private int parametersIndex;
    
    @Override
    public final SQLType getType() {
        return type;
    }
    
    @Override
    public int increaseParametersIndex() {
        return ++parametersIndex;
    }
}
