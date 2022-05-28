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

package com.dangdang.ddframe.rdb.sharding.parsing.parser.token;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.LinkedList;
import java.util.List;

/**
 * Select items token. 选择项标记对象。属于分片上下文信息，目前有 3 个情况会创建：1.AVG 查询额外 COUNT 和 SUM：#appendAvgDerivedColumns()
 * 2.GROUP BY 不在 查询字段，额外查询该字段 ：#appendDerivedOrderColumns()  3.ORDER BY 不在 查询字段，额外查询该字段 ：#appendDerivedOrderColumns()
 * @author zhangliang
 */
@RequiredArgsConstructor
@Getter
@ToString
public final class ItemsToken implements SQLToken {
    // SQL 开始位置
    private final int beginPosition;
    // 字段名数组
    private final List<String> items = new LinkedList<>();
}
