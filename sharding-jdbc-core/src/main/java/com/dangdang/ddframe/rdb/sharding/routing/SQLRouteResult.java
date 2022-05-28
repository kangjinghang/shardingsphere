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

package com.dangdang.ddframe.rdb.sharding.routing;

import com.dangdang.ddframe.rdb.sharding.parsing.parser.sql.SQLStatement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * SQL route result. 经过 SQL解析、SQL路由后，整个产生SQL路由结果，即 SQLRouteResult。根据路由结果，生成SQL，执行SQL。
 * 
 * @author gaohongtao
 * @author zhangliang
 */
@RequiredArgsConstructor
@Getter
public final class SQLRouteResult {
    // SQL语句对象，经过SQL解析的结果对象
    private final SQLStatement sqlStatement;
    // SQL最小执行单元集合。SQL执行时，执行每个单元。
    private final Set<SQLExecutionUnit> executionUnits = new LinkedHashSet<>();
    // 插入SQL语句生成的主键编号集合。目前不支持批量插入而使用集合的原因，猜测是为了未来支持批量插入做准备。
    private final List<Number> generatedKeys = new LinkedList<>();
}
